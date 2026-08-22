package com.termux.terminal;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * A terminal session, consisting of a remote process (e.g. a shell over SSH) coupled to a
 * terminal interface.
 * <p>
 * VibeTerm patch: the upstream (termux-app) class forked a local subprocess through JNI. This
 * fork removes all local pty/process handling and turns the class into a transport-agnostic
 * abstract base. A subclass supplies the byte transport (SSH channel, telnet, ...) through the
 * {@code onTransport*} methods, and feeds received bytes back via {@link #onTransportData} and
 * {@link #onTransportExited}. The upstream threading model is preserved: transport threads write
 * into {@link #mProcessToTerminalIOQueue} and all terminal emulation happens on the main thread.
 * <p>
 * When the size is made known by a call to {@link #updateSize(int, int, int, int)} terminal
 * emulation will begin and the transport will be started.
 * <p>
 * NOTE: The terminal session may outlive the EmulatorView, so be careful with callbacks!
 */
public abstract class TerminalSession extends TerminalOutput {

    private static final int MSG_NEW_INPUT = 1;
    private static final int MSG_PROCESS_EXITED = 4;

    public final String mHandle = UUID.randomUUID().toString();

    TerminalEmulator mEmulator;

    /**
     * A queue written to from transport threads when the remote outputs, and read by main thread
     * to process by terminal emulator.
     */
    final ByteQueue mProcessToTerminalIOQueue = new ByteQueue(64 * 1024);

    /** Buffer to write translate code points into utf8 before writing to the transport. */
    private final byte[] mUtf8InputBuffer = new byte[5];

    /** Callback which gets notified when a session finishes or changes title. */
    TerminalSessionClient mClient;

    /** Whether the transport is (or is about to be) running. Set false once the transport exits. */
    private volatile boolean mRunning = true;

    /** The exit status of the transport. Only valid if not {@link #isRunning()}. */
    int mExitStatus;

    /** Set by the application for user identification of session, not by terminal. */
    public String mSessionName;

    final Handler mMainThreadHandler = new MainThreadHandler();

    private final Integer mTranscriptRows;

    public TerminalSession(Integer transcriptRows, TerminalSessionClient client) {
        this.mTranscriptRows = transcriptRows;
        this.mClient = client;
    }

    /**
     * @param client The {@link TerminalSessionClient} interface implementation to allow
     *               for communication between {@link TerminalSession} and its client.
     */
    public void updateTerminalSessionClient(TerminalSessionClient client) {
        mClient = client;

        if (mEmulator != null)
            mEmulator.updateTerminalSessionClient(client);
    }

    /** Inform the attached transport of the new size and reflow or initialize the emulator. */
    public void updateSize(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        if (mEmulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels);
        } else {
            onTransportResize(columns, rows, cellWidthPixels, cellHeightPixels);
            mEmulator.resize(columns, rows, cellWidthPixels, cellHeightPixels);
        }
    }

    /** The terminal title as set through escape sequences or null if none set. */
    public String getTitle() {
        return (mEmulator == null) ? null : mEmulator.getTitle();
    }

    /**
     * Set the terminal emulator's window size and start terminal emulation and the transport.
     *
     * @param columns The number of columns in the terminal window.
     * @param rows    The number of rows in the terminal window.
     */
    public void initializeEmulator(int columns, int rows, int cellWidthPixels, int cellHeightPixels) {
        mEmulator = new TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, mTranscriptRows, mClient);
        onTransportStart(columns, rows, cellWidthPixels, cellHeightPixels);
    }

    // ------------------------------------------------------------------------------
    // Transport SPI - implemented by subclasses (called on the main thread).
    // ------------------------------------------------------------------------------

    /** Start the transport (e.g. open the SSH connection asynchronously). Terminal size is known at this point. */
    protected abstract void onTransportStart(int columns, int rows, int cellWidthPixels, int cellHeightPixels);

    /** Write bytes to the transport. Must not block the calling (main) thread. */
    protected abstract void onTransportWrite(byte[] data, int offset, int count);

    /** Propagate a terminal window size change to the transport (e.g. SSH window-change request). */
    protected abstract void onTransportResize(int columns, int rows, int cellWidthPixels, int cellHeightPixels);

    /** Forcefully terminate the transport. */
    protected abstract void onTransportKill();

    // ------------------------------------------------------------------------------
    // Transport callbacks - called by subclasses from any thread.
    // ------------------------------------------------------------------------------

    /** Feed bytes received from the remote into the terminal emulator (thread-safe). */
    public final void onTransportData(byte[] buffer, int offset, int length) {
        if (!mProcessToTerminalIOQueue.write(buffer, offset, length)) return;
        mMainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT);
    }

    /**
     * Notify that the transport has exited (thread-safe).
     *
     * @param exitStatus exit code of the remote command, or -1 if unknown/connection error.
     * @param message    optional human-readable message appended to the terminal, or null.
     */
    public final void onTransportExited(int exitStatus, String message) {
        mMainThreadHandler.sendMessage(mMainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, exitStatus, 0, message));
    }

    /** Write data to the remote process. */
    @Override
    public void write(byte[] data, int offset, int count) {
        if (mRunning) onTransportWrite(data, offset, count);
    }

    /** Write the Unicode code point to the terminal encoded in UTF-8. */
    public void writeCodePoint(boolean prependEscape, int codePoint) {
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            // 1114111 (= 2**16 + 1024**2 - 1) is the highest code point, [0xD800,0xDFFF] is the surrogate range.
            throw new IllegalArgumentException("Invalid code point: " + codePoint);
        }

        int bufferPosition = 0;
        if (prependEscape) mUtf8InputBuffer[bufferPosition++] = 27;

        if (codePoint <= /* 7 bits */0b1111111) {
            mUtf8InputBuffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= /* 11 bits */0b11111111111) {
            /* 110xxxxx leading byte with leading 5 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= /* 16 bits */0b1111111111111111) {
            /* 1110xxxx leading byte with leading 4 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else { /* We have checked codePoint <= 1114111 above, so we have max 21 bits = 0b111111111111111111111 */
            /* 11110xxx leading byte with leading 3 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            /* 10xxxxxx continuation byte with following 6 bits */
            mUtf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        }
        write(mUtf8InputBuffer, 0, bufferPosition);
    }

    public TerminalEmulator getEmulator() {
        return mEmulator;
    }

    /** Notify the {@link #mClient} that the screen has changed. */
    protected void notifyScreenUpdate() {
        mClient.onTextChanged(this);
    }

    /** Reset state for terminal emulator state. */
    public void reset() {
        mEmulator.reset();
        notifyScreenUpdate();
    }

    /** Finish this terminal session by killing the transport. */
    public void finishIfRunning() {
        if (isRunning()) {
            onTransportKill();
        }
    }

    /** Cleanup resources when the transport exits. */
    void cleanupResources(int exitStatus) {
        synchronized (this) {
            mRunning = false;
            mExitStatus = exitStatus;
        }
        mProcessToTerminalIOQueue.close();
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {
        mClient.onTitleChanged(this);
    }

    public synchronized boolean isRunning() {
        return mRunning;
    }

    /** Only valid if not {@link #isRunning()}. */
    public synchronized int getExitStatus() {
        return mExitStatus;
    }

    @Override
    public void onCopyTextToClipboard(String text) {
        mClient.onCopyTextToClipboard(this, text);
    }

    @Override
    public void onPasteTextFromClipboard() {
        mClient.onPasteTextFromClipboard(this);
    }

    @Override
    public void onBell() {
        mClient.onBell(this);
    }

    @Override
    public void onColorsChanged() {
        mClient.onColorsChanged(this);
    }

    @SuppressLint("HandlerLeak")
    class MainThreadHandler extends Handler {

        MainThreadHandler() {
            super(Looper.getMainLooper());
        }

        final byte[] mReceiveBuffer = new byte[64 * 1024];

        @Override
        public void handleMessage(Message msg) {
            int bytesRead = mProcessToTerminalIOQueue.read(mReceiveBuffer, false);
            if (bytesRead > 0 && mEmulator != null) {
                mEmulator.append(mReceiveBuffer, bytesRead);
                notifyScreenUpdate();
            }

            if (msg.what == MSG_PROCESS_EXITED) {
                int exitCode = msg.arg1;
                cleanupResources(exitCode);

                String exitDescription = "\r\n[" + ((msg.obj instanceof String) ? msg.obj : "Session finished");
                if (exitCode > 0) {
                    exitDescription += " (code " + exitCode + ")";
                }
                exitDescription += "]";

                if (mEmulator != null) {
                    byte[] bytesToWrite = exitDescription.getBytes(StandardCharsets.UTF_8);
                    mEmulator.append(bytesToWrite, bytesToWrite.length);
                    notifyScreenUpdate();
                }

                mClient.onSessionFinished(TerminalSession.this);
            }
        }

    }

}

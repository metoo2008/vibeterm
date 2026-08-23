package dev.vibeterm.ssh

/**
 * 输入背压的纯逻辑(与 Android/网络无关,便于单测)。
 * 按「待发送总字节数」限流,而非仅按队列条数——一次粘贴可能就是一个任意大的数组。
 */
object InputBackpressure {
    /** 待发送输入总字节上限(约 4 MiB)。 */
    const val MAX_PENDING_INPUT_BYTES = 4L * 1024 * 1024

    /**
     * 在已有 [pending] 字节待发送时,是否允许再入队 [count] 字节。
     * count<=0 或会超过上限则拒绝(恰好等于上限允许)。
     */
    fun canEnqueue(pending: Long, count: Int): Boolean =
        count > 0 && pending + count <= MAX_PENDING_INPUT_BYTES
}

package com.jm3.core.settings

/**
 * 上次刷新得到的线路池缓存。
 *
 * 接口是阻塞的：网关装配发生在首屏请求之前且同步执行，等不到挂起读取，
 * 所以这份「上次可用线路池」不能走异步的设置存储。
 */
interface DomainPoolCache {

    /** 上一次落盘的线路池；没有则为空表。 */
    fun read(): List<String>

    /** 覆盖落盘；入参先按 [Domains.normalize] 归一化。 */
    fun write(pool: List<String>)
}

/** 纯内存实现，供测试与缺省装配。 */
class InMemoryDomainPoolCache : DomainPoolCache {

    @Volatile
    private var pool: List<String> = emptyList()

    override fun read(): List<String> = pool

    override fun write(pool: List<String>) {
        this.pool = Domains.normalize(pool)
    }
}

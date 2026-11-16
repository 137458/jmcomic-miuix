package com.jm3.app.miuix.ui.route

import com.jm3.app.miuix.Route
import com.jm3.core.model.SearchScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 导航目标表：屏幕只表达语义动作，[Route] 的构造与「压栈还是同层替换」的取舍全在这一处。
 *
 * 这些用例锁的是装配根的对外契约，不是屏幕渲染：动作名与目标路由一一对应，
 * 换章这类同层替换不能退化成压栈，否则返回键会逐章回退。
 */
class AppRoutesTest {

    /** 把两条出口的调用顺序原样记下来，断言时能分辨「压了哪一页」与「换掉了栈顶」。 */
    private class Recorder {
        val pushed = mutableListOf<Route>()
        val replaced = mutableListOf<Route>()

        val routes = AppRoutes(
            push = { pushed += it },
            replaceTop = { replaced += it },
        )
    }

    @Test
    fun 无参页面全部压栈且不碰栈顶替换() {
        val recorder = Recorder()
        with(recorder.routes) {
            login()
            favorites()
            history()
            shelf()
            settings()
            update()
            week()
            notifications()
            tracking()
            tasks()
            profile()
            watchHistory()
            forum()
            novelList()
            library()
        }

        assertEquals(
            listOf(
                Route.Login,
                Route.Favorites,
                Route.History,
                Route.Shelf,
                Route.Settings,
                Route.Update,
                Route.Week,
                Route.Notifications,
                Route.Tracking,
                Route.Tasks,
                Route.Profile,
                Route.WatchHistory,
                Route.Forum,
                Route.Novel,
                Route.Library,
            ),
            recorder.pushed,
        )
        assertTrue(recorder.replaced.isEmpty(), "无参页面不该走同层替换")
    }

    @Test
    fun 作品详情只带作品号由详情页自取标题与封面() {
        val recorder = Recorder()
        recorder.routes.album("422333")

        assertEquals(listOf<Route>(Route.AlbumDetail("422333")), recorder.pushed)
        assertEquals("422333", (recorder.pushed.single() as Route.AlbumDetail).albumId)
        assertEquals(null, (recorder.pushed.single() as Route.AlbumDetail).initialTitle)
        assertEquals(null, (recorder.pushed.single() as Route.AlbumDetail).initialCoverUrl)
        assertTrue(recorder.replaced.isEmpty())
    }

    @Test
    fun 阅读器普通进入是压栈并保留起始页() {
        val recorder = Recorder()
        with(recorder.routes) {
            reader("123456")
            readerAt("123456", 7)
            readerAt("123456", null)
        }

        assertEquals<List<Route>>(
            listOf(
                Route.Reader("123456"),
                Route.Reader("123456", 7),
                Route.Reader("123456", null),
            ),
            recorder.pushed,
        )
        assertTrue(recorder.replaced.isEmpty(), "章节内翻页不换页，进入阅读器不该替换栈顶")
    }

    @Test
    fun 换章同层替换不新增返回层级() {
        val recorder = Recorder()
        recorder.routes.nextChapter("654321")

        assertEquals(listOf<Route>(Route.Reader("654321")), recorder.replaced)
        assertTrue(recorder.pushed.isEmpty(), "换章若压栈，返回键会逐章回退而不是回到详情页")
    }

    @Test
    fun 带参页面把参数原样带进路由() {
        val recorder = Recorder()
        with(recorder.routes) {
            novelDetail("novel-1")
            novelReader("novel-1", "chapter-9")
            creator("creator-2")
            creatorWork("work-3")
            search("白丝 校服", SearchScope.SITE)
            search("雨宫", SearchScope.WORK)
        }

        assertEquals(
            listOf(
                Route.NovelDetail("novel-1"),
                Route.NovelReader("novel-1", "chapter-9"),
                Route.Creator("creator-2"),
                Route.CreatorWork("work-3"),
                Route.Search("白丝 校服", SearchScope.SITE),
                Route.Search("雨宫", SearchScope.WORK),
            ),
            recorder.pushed,
        )
        assertTrue(recorder.replaced.isEmpty())
    }

    @Test
    fun 语义动作共二十四个改动须同步文档与屏幕() {
        val actions = AppRoutes::class.java.declaredMethods
            .filter { it.returnType == Void.TYPE }
            .map { it.name }
            .sorted()

        assertEquals(
            listOf(
                "album",
                "creator",
                "creatorWork",
                "favorites",
                "forum",
                "history",
                "library",
                "login",
                "nextChapter",
                "notifications",
                "novelDetail",
                "novelList",
                "novelReader",
                "profile",
                "reader",
                "readerAt",
                "search",
                "settings",
                "shelf",
                "tasks",
                "tracking",
                "update",
                "watchHistory",
                "week",
            ),
            actions,
        )
    }
}

package com.jm3.core

import com.jm3.core.model.TaskItem
import com.jm3.core.model.TaskPage
import io.github.jukomu.jmcomic.api.model.JmTaskItem
import io.github.jukomu.jmcomic.api.model.JmTaskList

/** 任务条目：金币为文本，无法解析时按 0。 */
fun JmTaskItem.toTaskItem(): TaskItem = TaskItem(
    id = id().orEmpty(),
    name = name().orEmpty(),
    content = content().orEmpty(),
    coin = coin()?.toIntOrNull() ?: 0,
    done = isDone,
    type = type(),
)

/** 任务列表：status 非 ok 时把 msg 作为失败原因带到 UI。 */
fun JmTaskList.toTaskPage(): TaskPage = TaskPage(
    items = list().orEmpty().map { it.toTaskItem() },
    message = msg()?.takeIf { it.isNotBlank() && !"ok".equals(status(), ignoreCase = true) },
)

package canary

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun unsafe(s: String?): Int = s!!.length                 // UnsafeCallOnNullableType
fun global(): Job = GlobalScope.launch { }               // GlobalCoroutineUsage
fun blocking(): Unit = runBlocking { }                   // ForbiddenMethodCall

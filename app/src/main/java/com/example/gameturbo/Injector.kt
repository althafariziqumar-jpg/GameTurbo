package com.example.gameturbo

import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.lang.reflect.Method
import java.util.concurrent.LinkedBlockingQueue

private var inputMgr: Any? = null
private var injectMethod: Method? = null
private var reported = false

private fun setup(): Boolean {
    return try {
        val sm = Class.forName("android.os.ServiceManager")
        val binder = sm.getMethod("getService", String::class.java).invoke(null, "input") as IBinder
        val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
        val mgr = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        inputMgr = mgr
        injectMethod = mgr!!.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        true
    } catch (e: Throwable) {
        false
    }
}

private fun inject(action: Int, x: Float, y: Float, down: Long) {
    val ev = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 1.0f, 1.0f, 0, 1.0f, 1.0f, 0, 0)
    ev.source = InputDevice.SOURCE_TOUCHSCREEN
    try {
        injectMethod!!.invoke(inputMgr, ev, 0)
    } catch (e: Throwable) {
        if (!reported) {
            reported = true
            println("ERR")
            System.out.flush()
        }
    }
    ev.recycle()
}

private fun tap(x: Float, y: Float, hold: Long) {
    val down = SystemClock.uptimeMillis()
    inject(MotionEvent.ACTION_DOWN, x, y, down)
    Thread.sleep(maxOf(10L, hold))
    inject(MotionEvent.ACTION_UP, x, y, down)
}

private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long) {
    val down = SystemClock.uptimeMillis()
    inject(MotionEvent.ACTION_DOWN, x1, y1, down)
    val steps = maxOf(2L, dur / 16)
    for (i in 1..steps) {
        Thread.sleep(maxOf(1L, dur / steps))
        val t = i.toFloat() / steps.toFloat()
        inject(MotionEvent.ACTION_MOVE, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, down)
    }
    inject(MotionEvent.ACTION_UP, x2, y2, down)
}

fun main(args: Array<String>) {
    if (!setup()) {
        println("FAIL")
        System.out.flush()
        return
    }
    println("READY")
    System.out.flush()
    val q = LinkedBlockingQueue<String>()
    val reader = Thread {
        try {
            while (true) {
                val line = readLine() ?: break
                if (q.size > 8) q.poll()
                q.add(line)
            }
        } catch (e: Throwable) {
        }
        q.add("Q")
    }
    reader.start()
    while (true) {
        val line = q.take()
        if (line == "Q") break
        val f = line.split(" ")
        try {
            if (f[0] == "T") {
                tap(f[1].toFloat(), f[2].toFloat(), f[3].toLong())
            } else if (f[0] == "S") {
                swipe(f[1].toFloat(), f[2].toFloat(), f[3].toFloat(), f[4].toFloat(), f[5].toLong())
            }
        } catch (e: Throwable) {
        }
    }
}

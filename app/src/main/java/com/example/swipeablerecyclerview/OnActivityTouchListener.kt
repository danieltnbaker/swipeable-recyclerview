package com.example.swipeablerecyclerview

import android.view.MotionEvent

interface OnActivityTouchListener {

    fun getTouchCoordinates(ev: MotionEvent?)
}
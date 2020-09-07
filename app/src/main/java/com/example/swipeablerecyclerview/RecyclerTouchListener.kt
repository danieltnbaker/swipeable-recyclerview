package com.example.swipeablerecyclerview

import android.animation.Animator
import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.ListView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener
import java.util.*
import kotlin.math.abs

class RecyclerTouchListener(
        private var activity: Activity,
        private val recyclerView: RecyclerView
) : OnItemTouchListener, OnActivityTouchListener {

    private var independentViews: MutableList<Int> = mutableListOf()
    private var optionViews: List<Int> = listOf()
    private var ignoredViewTypes: MutableSet<Int> = mutableSetOf()
    private var unSwipeableRows: List<Int> = listOf()

    // Cached ViewConfiguration and system-wide constant values
    private val touchSlop: Int
    private val minFlingVel: Int
    private val maxFlingVel: Int

    // private SwipeListener mSwipeListener;
    private var backgroundWidth = 1
    private val bgWidthLeft = 1 // 1 and not 0 to prevent dividing by zero

    // Transient properties
    private var dismissAnimationRefCount = 0
    private var touchedX = 0f
    private var touchedY = 0f
    private var swipingSlop = 0
    private var velocityTracker: VelocityTracker? = null
    private var touchedPosition = 0
    private var touchedView: View? = null
    private var isPaused = false
    private var isForegroundSwiping = false
    private var isForegroundPartialViewClicked = false
    private var isBackgroundVisible: Boolean = false
    private var backgroundVisiblePosition: Int = -1
    private var backgroundVisibleView: View? = null
    private var isRecyclerViewScrolling: Boolean = false
    private var heightOutsideRView = 0
    private var screenHeight = 0

    // Foreground view (to be swiped), Background view (to show)
    private var foregroundView: View? = null
    private var backgroundView: View? = null

    //view ID
    private var foregroundViewId = 0
    private var bgViewID = 0
    private val bgViewIDLeft = 0
    private var fadeViews: ArrayList<Int> = arrayListOf()
    private var mRowClickListener: OnRowClickListener? = null
    private var mBgClickListener: OnSwipeOptionsClickListener? = null
    private val mBgClickListenerLeft: OnSwipeOptionsClickListener? = null

    // user choices
    private var clickable = false
    private var swipeable = false
    private val swipeableLeftOptions = false

    init {
        val configuration = ViewConfiguration.get(recyclerView.context)
        touchSlop = configuration.scaledTouchSlop
        minFlingVel = configuration.scaledMinimumFlingVelocity * 16
        maxFlingVel = configuration.scaledMaximumFlingVelocity

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                /**
                 * This will ensure that this RecyclerTouchListener is paused during recycler view scrolling.
                 * If a scroll listener is already assigned, the caller should still pass scroll changes through
                 * to this listener.
                 */
                setEnabled(newState != RecyclerView.SCROLL_STATE_DRAGGING)
                /**
                 * This is used so that clicking a row cannot be done while scrolling
                 */
                isRecyclerViewScrolling = newState != RecyclerView.SCROLL_STATE_IDLE
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {}
        })
    }

    /**
     * Enables or disables (pauses or resumes) watching for swipe-to-dismiss gestures.
     *
     * @param enabled Whether or not to watch for gestures.
     */
    fun setEnabled(enabled: Boolean) {
        isPaused = !enabled
    }

    override fun onInterceptTouchEvent(rv: RecyclerView, motionEvent: MotionEvent): Boolean {
        return handleTouchEvent(motionEvent)
    }

    override fun onTouchEvent(rv: RecyclerView, motionEvent: MotionEvent) {
        handleTouchEvent(motionEvent)
    }

    fun setClickable(listener: OnRowClickListener?): RecyclerTouchListener {
        clickable = true
        mRowClickListener = listener
        return this
    }


    /**
     * independentViews are views on the foreground layer which when clicked, act "independent" from the foreground
     * ie, they are treated separately from the "row click" action
     *
     * @param viewIds ids of views to ignore when view clicked.
     */
    fun setIndependentViews(vararg viewIds: Int): RecyclerTouchListener {
        independentViews = mutableListOf(*viewIds.toTypedArray())
        return this
    }

    fun setSwipeable(foregroundId: Int, backgroundId: Int, listener: OnSwipeOptionsClickListener?): RecyclerTouchListener {
        swipeable = true
        require(!(foregroundViewId != 0 && foregroundId != foregroundViewId)) { "foregroundId does not match previously set Id" }
        foregroundViewId = foregroundId
        bgViewID = backgroundId
        mBgClickListener = listener
        if (activity is RecyclerTouchListenerHelper) (activity as RecyclerTouchListenerHelper).setOnActivityTouchListener(this)
        val displayMetrics = DisplayMetrics()
        activity.windowManager.defaultDisplay.getMetrics(displayMetrics)
        screenHeight = displayMetrics.heightPixels
        return this
    }

    fun setSwipeable(value: Boolean): RecyclerTouchListener {
        swipeable = value
        if (!value) invalidateSwipeOptions()
        return this
    }

    fun setSwipeOptionViews(vararg viewIds: Int): RecyclerTouchListener {
        optionViews = mutableListOf(*viewIds.toTypedArray())
        return this
    }

    fun setUnSwipeableRows(vararg rows: Int): RecyclerTouchListener {
        unSwipeableRows = mutableListOf(*rows.toTypedArray())
        return this
    }

    private fun isIndependentViewClicked(motionEvent: MotionEvent): Boolean {
        for (i in independentViews.indices) {
            if (touchedView != null) {
                val rect = Rect()
                val x = motionEvent.rawX.toInt()
                val y = motionEvent.rawY.toInt()
                touchedView!!.findViewById<View>(independentViews[i]).getGlobalVisibleRect(rect)
                if (rect.contains(x, y)) {
                    return false
                }
            }
        }
        return true
    }

    private fun getOptionViewID(motionEvent: MotionEvent): Int {
        for (i in optionViews.indices) {
            if (touchedView != null) {
                val rect = Rect()
                val x = motionEvent.rawX.toInt()
                val y = motionEvent.rawY.toInt()
                touchedView!!.findViewById<View>(optionViews[i]).getGlobalVisibleRect(rect)
                if (rect.contains(x, y)) {
                    return optionViews[i]
                }
            }
        }
        return -1
    }

    private fun getIndependentViewID(motionEvent: MotionEvent): Int {
        for (i in independentViews.indices) {
            if (touchedView != null) {
                val rect = Rect()
                val x = motionEvent.rawX.toInt()
                val y = motionEvent.rawY.toInt()
                touchedView!!.findViewById<View>(independentViews[i]).getGlobalVisibleRect(rect)
                if (rect.contains(x, y)) {
                    return independentViews[i]
                }
            }
        }
        return -1
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}

    fun invalidateSwipeOptions() {
        backgroundWidth = 1
    }

    fun closeVisibleBG(mSwipeCloseListener: OnSwipeListener?) {
        if (backgroundVisibleView == null) {
            Log.e(TAG, "No rows found for which background options are visible")
            return
        }
        val translateAnimator = ObjectAnimator.ofFloat(backgroundVisibleView,
                View.TRANSLATION_X, 0f)
        translateAnimator.duration = ANIMATION_CLOSE
        translateAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                mSwipeCloseListener?.onSwipeOptionsClosed()
                translateAnimator.removeAllListeners()
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        translateAnimator.start()
        animateFadeViews(backgroundVisibleView, 1f, ANIMATION_CLOSE)
        isBackgroundVisible = false
        backgroundVisibleView = null
        backgroundVisiblePosition = -1
    }

    private fun animateFadeViews(downView: View?, alpha: Float, duration: Long) {
        if (fadeViews != null) {
            for (viewID in fadeViews!!) {
                downView!!.findViewById<View>(viewID).animate()
                        .alpha(alpha).duration = duration
            }
        }
    }

    private fun animateForeground(downView: View?, animateType: Animation, duration: Long) {
        if (animateType == Animation.OPEN) {
            val translateAnimator = ObjectAnimator.ofFloat(
                    foregroundView, View.TRANSLATION_X, -backgroundWidth.toFloat())
            translateAnimator.duration = duration
            translateAnimator.interpolator = DecelerateInterpolator(1.5f)
            translateAnimator.start()
            animateFadeViews(downView, 0f, duration)
        } else if (animateType == Animation.CLOSE) {
            val translateAnimator = ObjectAnimator.ofFloat(
                    foregroundView, View.TRANSLATION_X, 0f)
            translateAnimator.duration = duration
            translateAnimator.interpolator = DecelerateInterpolator(1.5f)
            translateAnimator.start()
            animateFadeViews(downView, 1f, duration)
        }
    }

    private fun animateForeground(downView: View?, animateType: Animation, duration: Long, mSwipeCloseListener: OnSwipeListener?) {
        val translateAnimator: ObjectAnimator
        if (animateType == Animation.OPEN) {
            translateAnimator = ObjectAnimator.ofFloat(foregroundView, View.TRANSLATION_X, -backgroundWidth.toFloat())
            translateAnimator.duration = duration
            translateAnimator.interpolator = DecelerateInterpolator(1.5f)
            translateAnimator.start()
            animateFadeViews(downView, 0f, duration)
        } else {
            translateAnimator = ObjectAnimator.ofFloat(foregroundView, View.TRANSLATION_X, 0f)
            translateAnimator.duration = duration
            translateAnimator.interpolator = DecelerateInterpolator(1.5f)
            translateAnimator.start()
            animateFadeViews(downView, 1f, duration)
        }
        translateAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                if (mSwipeCloseListener != null) {
                    if (animateType == Animation.OPEN) mSwipeCloseListener.onSwipeOptionsOpened()
                    else if (animateType == Animation.CLOSE) mSwipeCloseListener.onSwipeOptionsClosed()
                }
                translateAnimator.removeAllListeners()
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
    }

    private fun handleTouchEvent(motionEvent: MotionEvent): Boolean {
        if (swipeable && backgroundWidth < 2) {
//            bgWidth = rView.getWidth();
            if (activity.findViewById<View?>(bgViewID) != null) backgroundWidth = activity.findViewById<View>(bgViewID).width
            heightOutsideRView = screenHeight - recyclerView.height
        }
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isPaused) {
                    return false
                }

                // Find the child view that was touched (perform a hit test)
                val rect = Rect()
                val childCount = recyclerView.childCount
                val listViewCoords = IntArray(2)
                recyclerView.getLocationOnScreen(listViewCoords)
                // x and y values respective to the recycler view
                var x = motionEvent.rawX.toInt() - listViewCoords[0]
                var y = motionEvent.rawY.toInt() - listViewCoords[1]
                var child: View

                /*
                 * check for every child (row) in the recycler view whether the touched co-ordinates belong to that
                 * respective child and if it does, register that child as the touched view (touchedView)
                 */
                var i = 0
                while (i < childCount) {
                    child = recyclerView.getChildAt(i)
                    child.getHitRect(rect)
                    if (rect.contains(x, y)) {
                        touchedView = child
                        break
                    }
                    i++
                }
                if (touchedView != null) {
                    touchedX = motionEvent.rawX
                    touchedY = motionEvent.rawY
                    touchedPosition = recyclerView.getChildAdapterPosition(touchedView!!)
                    if (shouldIgnoreAction(touchedPosition)) {
                        touchedPosition = ListView.INVALID_POSITION
                        return false // <-- guard here allows for ignoring events, allowing more than one view type and preventing NPE
                    }
                    if (swipeable) {
                        velocityTracker = VelocityTracker.obtain()
                        velocityTracker?.addMovement(motionEvent)
                        foregroundView = touchedView!!.findViewById(foregroundViewId)
                        backgroundView = touchedView!!.findViewById(bgViewID)
                        //                        bgView.getLayoutParams().height = fgView.getHeight();
                        backgroundView?.minimumHeight = foregroundView?.height ?: 1

                        /*
                         * bgVisible is true when the options menu is opened
                         * This block is to register fgPartialViewClicked status - Partial view is the view that is still
                         * shown on the screen if the options width is < device width
                         */if (isBackgroundVisible && foregroundView != null) {
                            x = motionEvent.rawX.toInt()
                            y = motionEvent.rawY.toInt()
                            foregroundView!!.getGlobalVisibleRect(rect)
                            isForegroundPartialViewClicked = rect.contains(x, y)
                        } else {
                            isForegroundPartialViewClicked = false
                        }
                    }
                }

                /*
                 * If options menu is shown and the touched position is not the same as the row for which the
                 * options is displayed - close the options menu for the row which is displaying it
                 * (bgVisibleView and bgVisiblePosition is used for this purpose which registers which view and
                 * which position has it's options menu opened)
                 */
                recyclerView.getHitRect(rect)
                if (swipeable && isBackgroundVisible && touchedPosition != backgroundVisiblePosition) {
                    closeVisibleBG(null)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (velocityTracker == null) {
                    return false
                }
                if (swipeable) {
                    if (touchedView != null && isForegroundSwiping) {
                        // cancel
                        animateForeground(touchedView, Animation.CLOSE, ANIMATION_STANDARD)
                    }
                    velocityTracker!!.recycle()
                    velocityTracker = null
                    isForegroundSwiping = false
                    backgroundView = null
                }
                touchedX = 0f
                touchedY = 0f
                touchedView = null
                touchedPosition = ListView.INVALID_POSITION
            }
            MotionEvent.ACTION_UP -> {
                run {
                    if (velocityTracker == null && swipeable) {
                        return false
                    }
                    if (touchedPosition < 0) return false

                    // swipedLeft and swipedRight are true if the user swipes in the respective direction (no conditions)
                    var swipedLeft = false
                    var swipedRight = false
                    /*
                 * swipedLeftProper and swipedRightProper are true if user swipes in the respective direction
                 * and if certain conditions are satisfied (given some few lines below)
                 */
                    var swipedLeftProper = false
                    var swipedRightProper = false
                    val mFinalDelta = motionEvent.rawX - touchedX

                    // if swiped in a direction, make that respective variable true
                    if (isForegroundSwiping) {
                        swipedLeft = mFinalDelta < 0
                        swipedRight = mFinalDelta > 0
                    }

                    /*
                 * If the user has swiped more than half of the width of the options menu, or if the
                 * velocity of swiping is between min and max fling values
                 * "proper" variable are set true
                 */if (Math.abs(mFinalDelta) > backgroundWidth / 2 && isForegroundSwiping) {
                    swipedLeftProper = mFinalDelta < 0
                    swipedRightProper = mFinalDelta > 0
                } else if (swipeable) {
                    velocityTracker!!.addMovement(motionEvent)
                    velocityTracker!!.computeCurrentVelocity(1000)
                    val velocityX = velocityTracker!!.xVelocity
                    val absVelocityX = Math.abs(velocityX)
                    val absVelocityY = Math.abs(velocityTracker!!.yVelocity)
                    if (minFlingVel <= absVelocityX && absVelocityX <= maxFlingVel && absVelocityY < absVelocityX && isForegroundSwiping) {
                        // dismiss only if flinging in the same direction as dragging
                        swipedLeftProper = velocityX < 0 == mFinalDelta < 0
                        swipedRightProper = velocityX > 0 == mFinalDelta > 0
                    }
                }

                    ///////// Manipulation of view based on the 4 variables mentioned above ///////////

                    // if swiped left properly and options menu isn't already visible, animate the foreground to the left
                    if (swipeable && !swipedRight && swipedLeftProper && touchedPosition != RecyclerView.NO_POSITION && !unSwipeableRows.contains(touchedPosition) && !isBackgroundVisible) {
                        val downPosition = touchedPosition
                        ++dismissAnimationRefCount
                        animateForeground(touchedView, Animation.OPEN, ANIMATION_STANDARD)
                        isBackgroundVisible = true
                        backgroundVisibleView = foregroundView
                        backgroundVisiblePosition = downPosition
                    } else if (swipeable && !swipedLeft && swipedRightProper && touchedPosition != RecyclerView.NO_POSITION && !unSwipeableRows.contains(touchedPosition) && isBackgroundVisible) {
                        // dismiss
                        ++dismissAnimationRefCount
                        animateForeground(touchedView, Animation.CLOSE, ANIMATION_STANDARD)
                        isBackgroundVisible = false
                        backgroundVisibleView = null
                        backgroundVisiblePosition = -1
                    } else if (swipeable && swipedLeft && !isBackgroundVisible) {
                        // cancel
                        val tempBgView = backgroundView
                        animateForeground(touchedView, Animation.CLOSE, ANIMATION_STANDARD, object : OnSwipeListener {
                            override fun onSwipeOptionsClosed() {
                                if (tempBgView != null) tempBgView.visibility = View.VISIBLE
                            }

                            override fun onSwipeOptionsOpened() {}
                        })
                        isBackgroundVisible = false
                        backgroundVisibleView = null
                        backgroundVisiblePosition = -1
                    } else if (swipeable && swipedRight && isBackgroundVisible) {
                        // cancel
                        animateForeground(touchedView, Animation.OPEN, ANIMATION_STANDARD)
                        isBackgroundVisible = true
                        backgroundVisibleView = foregroundView
                        backgroundVisiblePosition = touchedPosition
                    } else if (swipeable && swipedRight && !isBackgroundVisible) {
                        // cancel
                        animateForeground(touchedView, Animation.CLOSE, ANIMATION_STANDARD)
                        isBackgroundVisible = false
                        backgroundVisibleView = null
                        backgroundVisiblePosition = -1
                    } else if (swipeable && swipedLeft && isBackgroundVisible) {
                        // cancel
                        animateForeground(touchedView, Animation.OPEN, ANIMATION_STANDARD)
                        isBackgroundVisible = true
                        backgroundVisibleView = foregroundView
                        backgroundVisiblePosition = touchedPosition
                    } else if (!swipedRight && !swipedLeft) {
                        // if partial foreground view is clicked (see ACTION_DOWN) bring foreground back to original position
                        // bgVisible is true automatically since it's already checked in ACTION_DOWN block
                        if (swipeable && isForegroundPartialViewClicked) {
                            animateForeground(touchedView, Animation.CLOSE, ANIMATION_STANDARD)
                            isBackgroundVisible = false
                            backgroundVisibleView = null
                            backgroundVisiblePosition = -1
                        } else if (clickable && !isBackgroundVisible && touchedPosition >= 0 && isIndependentViewClicked(motionEvent) && !isRecyclerViewScrolling) {
                            mRowClickListener!!.onRowClicked(touchedPosition)
                        } else if (clickable && !isBackgroundVisible && touchedPosition >= 0 && !isIndependentViewClicked(motionEvent) && !isRecyclerViewScrolling) {
                            val independentViewID = getIndependentViewID(motionEvent)
                            if (independentViewID >= 0) mRowClickListener!!.onIndependentViewClicked(independentViewID, touchedPosition)
                        } else if (swipeable && isBackgroundVisible && !isForegroundPartialViewClicked) {
                            val optionID = getOptionViewID(motionEvent)
                            if (optionID >= 0 && touchedPosition >= 0) {
                                val downPosition = touchedPosition
                                closeVisibleBG(object : OnSwipeListener {
                                    override fun onSwipeOptionsClosed() {
                                        mBgClickListener!!.onSwipeOptionClicked(optionID, downPosition)
                                    }

                                    override fun onSwipeOptionsOpened() {}
                                })
                            }
                        }
                    }
                }
                // if clicked and not swiped
                if (swipeable) {
                    velocityTracker!!.recycle()
                    velocityTracker = null
                }
                touchedX = 0f
                touchedY = 0f
                touchedView = null
                touchedPosition = ListView.INVALID_POSITION
                isForegroundSwiping = false
                backgroundView = null
            }
            MotionEvent.ACTION_MOVE -> {
                if (velocityTracker == null || isPaused || !swipeable) {
                    return false
                }
                velocityTracker!!.addMovement(motionEvent)
                val deltaX = motionEvent.rawX - touchedX
                val deltaY = motionEvent.rawY - touchedY

                /*
                 * isFgSwiping variable which is set to true here is used to alter the swipedLeft, swipedRightProper
                 * variables in "ACTION_UP" block by checking if user is actually swiping at present or not
                 */if (!isForegroundSwiping && abs(deltaX) > touchSlop && abs(deltaY) < abs(deltaX) / 2) {
                    isForegroundSwiping = true
                    swipingSlop = if (deltaX > 0) touchSlop else -touchSlop
                }

                // This block moves the foreground along with the finger when swiping
                if (swipeable && isForegroundSwiping && !unSwipeableRows.contains(touchedPosition)) {
                    if (backgroundView == null) {
                        backgroundView = touchedView!!.findViewById(bgViewID)
                        backgroundView?.visibility = View.VISIBLE
                    }
                    // if fg is being swiped left
                    if (deltaX < touchSlop && !isBackgroundVisible) {
                        val translateAmount = deltaX - swipingSlop
                        //                        if ((Math.abs(translateAmount) > bgWidth ? -bgWidth : translateAmount) <= 0) {
                        // swipe fg till width of bg. If swiped further, nothing happens (stalls at width of bg)
                        foregroundView!!.translationX = if (abs(translateAmount) > backgroundWidth) -backgroundWidth.toFloat() else translateAmount
                        if (foregroundView!!.translationX > 0) foregroundView!!.translationX = 0f
                        //                        }

                        // fades all the fadeViews gradually to 0 alpha as dragged
                        if (fadeViews.isNotEmpty()) {
                            for (viewID in fadeViews) {
                                touchedView!!.findViewById<View>(viewID).alpha = 1 - abs(translateAmount) / backgroundWidth
                            }
                        }
                    } else if (deltaX > 0 && isBackgroundVisible) {
                        // for closing rightOptions
                        if (isBackgroundVisible) {
                            val translateAmount = deltaX - swipingSlop - backgroundWidth

                            // swipe fg till it reaches original position. If swiped further, nothing happens (stalls at 0)
                            foregroundView!!.translationX = if (translateAmount > 0f) 0f else translateAmount

                            // fades all the fadeViews gradually to 0 alpha as dragged
                            if (fadeViews.isNotEmpty()) {
                                for (viewID in fadeViews) {
                                    touchedView!!.findViewById<View>(viewID).alpha = 1 - abs(translateAmount) / backgroundWidth
                                }
                            }
                        } else {
                            val translateAmount = deltaX - swipingSlop - backgroundWidth

                            // swipe fg till it reaches original position. If swiped further, nothing happens (stalls at 0)
                            foregroundView!!.translationX = if (translateAmount > 0f) 0f else translateAmount

                            // fades all the fadeViews gradually to 0 alpha as dragged
                            if (fadeViews.isNotEmpty()) {
                                for (viewID in fadeViews) {
                                    touchedView!!.findViewById<View>(viewID).alpha = 1 - abs(translateAmount) / backgroundWidth
                                }
                            }
                        }
                    }
                    return true
                } else if (swipeable && isForegroundSwiping && unSwipeableRows.contains(touchedPosition)) {
                    if (deltaX < touchSlop && !isBackgroundVisible) {
                        val translateAmount = deltaX - swipingSlop
                        if (backgroundView == null) backgroundView = touchedView!!.findViewById(bgViewID)
                        if (backgroundView != null) backgroundView?.visibility = View.GONE

                        // swipe fg till width of bg. If swiped further, nothing happens (stalls at width of bg)
                        foregroundView!!.translationX = translateAmount / 5
                        if (foregroundView!!.translationX > 0) foregroundView!!.translationX = 0f
                    }
                    return true
                }
            }
        }
        return false
    }

    /**
     * Gets coordinates from Activity and closes any
     * swiped rows if touch happens outside the recycler view
     */
    override fun getTouchCoordinates(ev: MotionEvent?) {
        val y = ev!!.rawY.toInt()
        if (swipeable && isBackgroundVisible && ev.actionMasked == MotionEvent.ACTION_DOWN && y < heightOutsideRView) {
            closeVisibleBG(null)
        }
    }

    private fun shouldIgnoreAction(touchedPosition: Int): Boolean {
        return ignoredViewTypes.contains(recyclerView.adapter?.getItemViewType(touchedPosition))
    }

    private enum class Animation {
        OPEN, CLOSE
    }

    interface OnRowClickListener {
        fun onRowClicked(position: Int)
        fun onIndependentViewClicked(independentViewID: Int, position: Int)
    }

    interface OnSwipeOptionsClickListener {
        fun onSwipeOptionClicked(viewId: Int, position: Int)
    }

    interface RecyclerTouchListenerHelper {
        fun setOnActivityTouchListener(listener: OnActivityTouchListener?)
    }

    interface OnSwipeListener {
        fun onSwipeOptionsClosed()
        fun onSwipeOptionsOpened()
    }

    companion object {
        private const val TAG = "RecyclerTouchListener"
        private const val ANIMATION_CLOSE: Long = 150
        private const val ANIMATION_STANDARD: Long = 300
    }
}
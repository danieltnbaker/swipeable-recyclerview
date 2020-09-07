package com.example.swipeablerecyclerview

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.swipeablerecyclerview.RecyclerTouchListener.OnRowClickListener
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val taskList: MutableList<Task> = ArrayList()
        var task = Task("Buy Dress", "Buy Dress at Shoppershop for coming functions")
        taskList.add(task)
        task = Task("Go For Walk", "Wake up 6AM go for walking")
        taskList.add(task)
        task = Task("Office Work", "Complete the office works on Time")
        taskList.add(task)
        task = Task("watch Repair", "Give watch to service center")
        taskList.add(task)
        task = Task("Recharge Mobile", "Recharge for 10$ to my **** number")
        taskList.add(task)
        task = Task("Read book", "Read android book completely")
        taskList.add(task)

        val recyclerviewAdapter = SwipeableRecyclerViewAdapter(this)
        recyclerviewAdapter.setTaskList(taskList)
        recyclerView.adapter = recyclerviewAdapter

        val touchListener = RecyclerTouchListener(this, recyclerView)
        touchListener
                .setClickable(object : OnRowClickListener {
                    override fun onRowClicked(position: Int) {
                        Toast.makeText(this@MainActivity, "Clicked on task: ${taskList[position].name}", Toast.LENGTH_SHORT).show()
                    }

                    override fun onIndependentViewClicked(independentViewID: Int, position: Int) {}
                })
                .setIndependentViews(R.id.shareIcon)
                .setSwipeOptionViews(R.id.deleteIcon, R.id.editIcon)
                .setSwipeable(R.id.rowForeground, R.id.rowBackground, object : RecyclerTouchListener.OnSwipeOptionsClickListener {
                    override fun onSwipeOptionClicked(viewId: Int, position: Int) {
                        when (viewId) {
                            R.id.deleteIcon -> {
                                taskList.removeAt(position)
                                recyclerviewAdapter.setTaskList(taskList)
                            }
                            R.id.editIcon -> Toast.makeText(this@MainActivity, "Edit Not Available", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
        recyclerView.addOnItemTouchListener(touchListener)
    }
}
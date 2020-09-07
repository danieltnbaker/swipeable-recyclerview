package com.example.swipeablerecyclerview

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.adapter_task_item.view.*
import java.util.*

class SwipeableRecyclerViewAdapter(private val context: Context) : RecyclerView.Adapter<SwipeableRecyclerViewAdapter.ViewHolder>() {

    private var taskList: List<Task> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.adapter_task_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(taskList[position])
    }

    override fun getItemCount(): Int {
        return taskList.size
    }

    fun setTaskList(taskList: List<Task>) {
        this.taskList = taskList
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(task: Task) {
            itemView.taskName.text = task.name
            itemView.taskDescription.text = task.description

            itemView.shareIcon.setOnClickListener {
                Toast.makeText(context, "Share task: ${task.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
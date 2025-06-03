package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Interface for click listeners on user items and delete button
interface OnUserActionListener {
    fun onEditClick(user: User)
    fun onDeleteClick(userId: String)
}

class UserAdapter(
    initialUsers: List<User>,
    private val listener: OnUserActionListener
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    private val TAG = "UserAdapter"
    private var users: MutableList<User> = initialUsers.toMutableList()

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUsername: TextView = itemView.findViewById(R.id.tv_user_username)
        val tvFullname: TextView = itemView.findViewById(R.id.tv_user_fullname)
        val tvRole: TextView = itemView.findViewById(R.id.tv_user_role)
        val userLayout: LinearLayout = itemView.findViewById(R.id.user_item_layout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        Log.d(TAG, "onCreateViewHolder: Creating new ViewHolder")
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        if (position < 0 || position >= users.size) {
            Log.e(TAG, "onBindViewHolder: Invalid position $position. Users size: ${users.size}")
            return
        }
        val currentUser = users[position]
        Log.d(TAG, "onBindViewHolder: Binding user at position $position, Username: ${currentUser.username}")

        holder.tvUsername.text = "${currentUser.username}"
        holder.tvFullname.text = "${currentUser.firstName} ${currentUser.lastName}"
        holder.tvRole.text = "${currentUser.role.name}"

        // Set click listener for the entire item (for editing)
        holder.userLayout.setOnClickListener {
            listener.onEditClick(currentUser)
        }


    }

    override fun getItemCount(): Int {
        val count = users.size
        Log.d(TAG, "getItemCount: Returning total user count: $count")
        return count
    }

    // Method to update the data in the adapter and refresh the RecyclerView
    fun updateData(newUsers: List<User>) {
        Log.d(TAG, "updateData: Received ${newUsers.size} new users. Replacing internal list.")
        users = newUsers.toMutableList()
        notifyDataSetChanged() // Notifies the RecyclerView that the data has changed
    }

    // Method to remove an item from the adapter's list (for immediate UI update after delete)
    fun removeItem(userId: String) {
        val index = users.indexOfFirst { it.id == userId }
        if (index != -1) {
            users.removeAt(index)
            notifyItemRemoved(index)
            Log.d(TAG, "Removed user with ID $userId from adapter. New size: ${users.size}")
        }
    }
}
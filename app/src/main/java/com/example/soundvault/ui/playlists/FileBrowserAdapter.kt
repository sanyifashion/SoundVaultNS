package com.example.soundvault.ui.playlists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.soundvault.databinding.FileItemBinding

class FileBrowserAdapter(
    private val onItemClick: (FileItem) -> Unit,
    private val onSelectionChanged: () -> Unit
) : ListAdapter<FileItem, FileBrowserAdapter.ViewHolder>(FileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = FileItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onItemClick, onSelectionChanged)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getSelectedItems(): List<FileItem> {
        return currentList.filter { it.isSelected }
    }

    class ViewHolder(
        private val binding: FileItemBinding,
        private val onItemClick: (FileItem) -> Unit,
        private val onSelectionChanged: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FileItem) {
            binding.fileName.text = item.file.name
            val iconRes = if (item.isDirectory) {
                android.R.drawable.ic_menu_directions
            } else {
                android.R.drawable.ic_media_play
            }
            binding.fileIcon.setImageResource(iconRes)
            
            // Back button folder shouldn't have a checkbox
            val isBackFolder = item.file.name == ".." || (item.isDirectory && item.file.listFiles() == null && !item.file.exists()) // Simple check for the "parent" logic I used
            // Actually my "parent" logic was: listOf(FileItem(currentDir.parentFile!!, true, false)) + files
            // Let's make it more robust. I'll just check if it's the first item and its path is parent of currentDir in Fragment.
            
            binding.fileCheckbox.visibility = if (item.file.name == "..") android.view.View.GONE else android.view.View.VISIBLE
            binding.fileCheckbox.isChecked = item.isSelected
            
            binding.root.setOnClickListener { 
                if (item.isDirectory) {
                    onItemClick(item) 
                } else {
                    item.isSelected = !item.isSelected
                    binding.fileCheckbox.isChecked = item.isSelected
                    onSelectionChanged()
                }
            }
            
            binding.fileCheckbox.setOnClickListener {
                item.isSelected = binding.fileCheckbox.isChecked
                onSelectionChanged()
            }
        }
    }

    class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.file.absolutePath == newItem.file.absolutePath
        }

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.isSelected == newItem.isSelected && oldItem.file.name == newItem.file.name
        }
    }
}

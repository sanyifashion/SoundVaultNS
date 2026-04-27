package com.example.soundvault.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.soundvault.R
import com.example.soundvault.data.Music

class MusicAdapter(
    var musicList: List<Music>,
    private val onItemClicked: (Int) -> Unit
) : RecyclerView.Adapter<MusicAdapter.ViewHolder>() {

    private var isShuffleEnabled: Boolean = false
    private var currentPlayingPosition: Int = -1
    
    // Multi-selection state
    var isMultiSelectMode = false
        set(value) {
            field = value
            if (!value) selectedItems.clear()
            notifyDataSetChanged()
        }
    val selectedItems = mutableSetOf<Music>()
    var onLongClick: (() -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.music_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val music = musicList[position]
        holder.title.text = music.title
        holder.artist.text = music.artist
        
        Glide.with(holder.itemView.context)
            .load(music.artUri)
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .into(holder.albumArt)
        
        // Multi-select checkbox
        val checkBox = holder.itemView.findViewById<CheckBox>(R.id.selection_checkbox)
        checkBox.visibility = if (isMultiSelectMode) View.VISIBLE else View.GONE
        checkBox.isChecked = selectedItems.contains(music)

        holder.itemView.setOnClickListener {
            if (isMultiSelectMode) {
                toggleSelection(music)
            } else {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onItemClicked(currentPos)
                }
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!isMultiSelectMode) {
                isMultiSelectMode = true
                toggleSelection(music)
                onLongClick?.invoke()
                true
            } else false
        }

        holder.itemView.isActivated = (currentPlayingPosition == position)
    }

    private fun toggleSelection(music: Music) {
        if (selectedItems.contains(music)) {
            selectedItems.remove(music)
        } else {
            selectedItems.add(music)
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = musicList.size

    fun updateMusic(newMusicList: List<Music>) {
        musicList = newMusicList
        notifyDataSetChanged()
    }

    fun updateShuffleState(enabled: Boolean) {
        isShuffleEnabled = enabled
        notifyDataSetChanged()
    }

    fun setCurrentPlayingPosition(position: Int) {
        val previousPosition = currentPlayingPosition
        currentPlayingPosition = position
        if (previousPosition != -1) notifyItemChanged(previousPosition)
        if (currentPlayingPosition != -1) notifyItemChanged(currentPlayingPosition)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.music_title)
        val artist: TextView = itemView.findViewById(R.id.music_artist)
        val albumArt: ImageView = itemView.findViewById(R.id.album_art)
    }
}

package com.example.soundvault.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.soundvault.R
import com.example.soundvault.data.Music

class MusicAdapter(
    private var musicList: List<Music>,
    private val onItemClicked: (Int) -> Unit
) : RecyclerView.Adapter<MusicAdapter.ViewHolder>() {

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
        
        holder.itemView.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onItemClicked(currentPos)
            }
        }
    }

    override fun getItemCount(): Int {
        return musicList.size
    }

    fun updateMusic(newMusicList: List<Music>) {
        musicList = newMusicList
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.music_title)
        val artist: TextView = itemView.findViewById(R.id.music_artist)
        val albumArt: ImageView = itemView.findViewById(R.id.album_art)
    }
}
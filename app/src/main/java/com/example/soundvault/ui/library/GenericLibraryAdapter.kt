package com.example.soundvault.ui.library

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.soundvault.R

class GenericLibraryAdapter(
    private val titles: List<String>,
    private val subtitles: List<String>,
    private val imageUris: List<Uri?>,
    @LayoutRes private val layoutId: Int = R.layout.music_item,
    private val onItemClicked: (Int) -> Unit
) : RecyclerView.Adapter<GenericLibraryAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.title.text = titles[position]
        holder.subtitle.text = subtitles[position]
        
        Glide.with(holder.itemView.context)
            .load(imageUris[position])
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .into(holder.image)
        
        holder.itemView.setOnClickListener {
            onItemClicked(position)
        }
    }

    override fun getItemCount(): Int = titles.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.music_title)
        val subtitle: TextView = itemView.findViewById(R.id.music_artist)
        val image: ImageView = itemView.findViewById(R.id.album_art)
    }
}

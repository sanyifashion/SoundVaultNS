package com.example.soundvault.ui.playlists

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soundvault.databinding.FragmentFileBrowserBinding
import java.io.File

class FileBrowserFragment : Fragment() {

    private var _binding: FragmentFileBrowserBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: FileBrowserAdapter
    private var currentDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
    private var playlistId: Long = -1
    private lateinit var viewModel: PlaylistDetailsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playlistId = arguments?.getLong("playlistId") ?: -1
        viewModel = ViewModelProvider(this)[PlaylistDetailsViewModel::class.java]

        adapter = FileBrowserAdapter(
            onItemClick = { item ->
                if (item.file.name == "..") {
                    currentDir = currentDir.parentFile ?: currentDir
                    updateFileList()
                } else if (item.isDirectory) {
                    currentDir = item.file
                    updateFileList()
                }
            },
            onSelectionChanged = {
                updateAddButtonText()
            }
        )

        binding.fileListRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.fileListRecyclerView.adapter = adapter

        binding.addCurrentFolderButton.setOnClickListener {
            val selectedItems = adapter.getSelectedItems()
            if (selectedItems.isNotEmpty()) {
                addSelectedItems(selectedItems)
            } else {
                addAllFromCurrentFolder()
            }
        }

        updateFileList()
        updateAddButtonText()
    }

    private fun updateAddButtonText() {
        val selectedCount = adapter.getSelectedItems().size
        binding.addCurrentFolderButton.text = if (selectedCount > 0) {
            "Add $selectedCount selected items"
        } else {
            "Add all from this folder"
        }
    }

    private fun updateFileList() {
        binding.currentPathText.text = currentDir.absolutePath
        val files = currentDir.listFiles()?.map { 
            FileItem(it, it.isDirectory, isMusicFile(it))
        }?.sortedWith(compareBy({ !it.isDirectory }, { it.file.name })) ?: emptyList()
        
        val listWithBack = if (currentDir.parentFile != null && currentDir.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
             val backFile = File(currentDir.parent, "..")
             listOf(FileItem(backFile, true, false)) + files
        } else {
            files
        }
        
        adapter.submitList(listWithBack)
        updateAddButtonText()
    }

    private fun isMusicFile(file: File): Boolean {
        val extensions = listOf("mp3", "wav", "m4a", "ogg", "flac")
        return file.isFile && extensions.contains(file.extension.lowercase())
    }

    private fun addSelectedItems(items: List<FileItem>) {
        val allMusic = viewModel.getAllSongs()
        items.forEach { item ->
            if (item.isDirectory) {
                addSongsFromDirRecursive(item.file, allMusic)
            } else {
                val music = allMusic.find { it.path == item.file.absolutePath }
                music?.let { viewModel.addSongToPlaylist(playlistId, it.id) }
            }
        }
        findNavController().popBackStack()
    }

    private fun addSongsFromDirRecursive(dir: File, allMusic: List<com.example.soundvault.data.Music>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                addSongsFromDirRecursive(file, allMusic)
            } else if (isMusicFile(file)) {
                val music = allMusic.find { it.path == file.absolutePath }
                music?.let { viewModel.addSongToPlaylist(playlistId, it.id) }
            }
        }
    }

    private fun addAllFromCurrentFolder() {
        val allMusic = viewModel.getAllSongs()
        currentDir.listFiles()?.forEach { file ->
            if (isMusicFile(file)) {
                val music = allMusic.find { it.path == file.absolutePath }
                music?.let {
                    viewModel.addSongToPlaylist(playlistId, it.id)
                }
            }
        }
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

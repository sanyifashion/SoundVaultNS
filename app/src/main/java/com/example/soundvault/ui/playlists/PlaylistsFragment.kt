package com.example.soundvault.ui.playlists

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.soundvault.R
import com.example.soundvault.data.Playlist
import com.example.soundvault.databinding.FragmentPlaylistsBinding

class PlaylistsFragment : Fragment() {

    private var _binding: FragmentPlaylistsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: PlaylistsViewModel
    private lateinit var adapter: PlaylistsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[PlaylistsViewModel::class.java]

        adapter = PlaylistsAdapter(
            onClick = { playlist ->
                val bundle = Bundle().apply {
                    putLong("playlistId", playlist.id)
                    putString("playlistName", playlist.name)
                }
                findNavController().navigate(R.id.action_nav_playlists_to_playlistDetailsFragment, bundle)
            },
            onDelete = { playlist ->
                showDeleteConfirmation(playlist)
            }
        )

        binding.playlistsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.playlistsRecyclerView.adapter = adapter

        viewModel.allPlaylists.observe(viewLifecycleOwner) { playlists ->
            adapter.submitList(playlists)
        }

        binding.addPlaylistFab.setOnClickListener {
            showAddPlaylistDialog()
        }
    }

    private fun showAddPlaylistDialog() {
        val editText = EditText(requireContext())
        AlertDialog.Builder(requireContext())
            .setTitle("New Playlist")
            .setMessage("Enter playlist name")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    viewModel.insert(Playlist(name = name))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteConfirmation(playlist: Playlist) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Playlist")
            .setMessage("Are you sure you want to delete '${playlist.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.delete(playlist)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

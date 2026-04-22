package com.wooma.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.wooma.databinding.FragmentMessagesBinding
import android.content.Intent
import com.wooma.storage.Prefs
import im.crisp.client.external.ChatActivity
import im.crisp.client.external.Crisp

class MessagesFragment : Fragment() {

    companion object {
        var autoOpenChat = false
    }

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentMessagesBinding.inflate(inflater, container, false)
            .also { _binding = it }
            .root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = Prefs.getUser(requireContext())
        if (user != null) {
            Crisp.setUserEmail(user.email)
            Crisp.setUserNickname("${user.first_name} ${user.last_name}".trim())
        }

        binding.btnOpenChat.setOnClickListener {
            startActivity(Intent(requireContext(), ChatActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

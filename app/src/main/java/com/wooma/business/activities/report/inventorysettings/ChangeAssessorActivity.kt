package com.wooma.business.activities.report.inventorysettings

import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import com.wooma.business.R
import com.wooma.business.activities.BaseActivity
import com.wooma.business.databinding.ActivityChangeAssessorBinding
import com.wooma.business.databinding.ActivityChangeReportTypeBinding

class ChangeAssessorActivity : BaseActivity() {
    private lateinit var binding: ActivityChangeAssessorBinding

    private val assessorsList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChangeAssessorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        assessorsList.add("Adam Malik")
        assessorsList.add("Fraser Mair")

        binding.tvReportChangeTo.setOnClickListener { view ->
            if (assessorsList.isEmpty()) {
                Toast.makeText(this, "No assessor available", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val popup = PopupMenu(
                this,
                view,
                Gravity.START,
                0,
                R.style.PopupMenuStyle
            )

            assessorsList.forEachIndexed { index, option ->
                popup.menu.add(
                    Menu.NONE,
                    index,          // itemId
                    index,          // order
                    option
                )
            }

            popup.setOnMenuItemClickListener { menuItem ->
                val selectedOption = assessorsList[menuItem.itemId]

                binding.tvReportChangeTo.text = selectedOption
                true
            }

            popup.show()
        }

        binding.ivBack.setOnClickListener { finish() }
    }
}
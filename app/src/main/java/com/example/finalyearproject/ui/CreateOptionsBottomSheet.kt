package com.example.finalyearproject.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.finalyearproject.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

/**
 * CreateOptionsBottomSheet
 *
 * Shown when the center FAB is tapped.
 * Offers: Upload Recipe | Write Blog | (future: Upload Video)
 */
class CreateOptionsBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_create, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.btn_upload_recipe)?.setOnClickListener {
            dismiss()
            Toast.makeText(requireContext(), "Upload Recipe — coming soon", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<MaterialButton>(R.id.btn_write_blog)?.setOnClickListener {
            dismiss()
            Toast.makeText(requireContext(), "Write Blog — coming soon", Toast.LENGTH_SHORT).show()
        }
    }
}
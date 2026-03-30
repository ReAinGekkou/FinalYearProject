package com.example.finalyearproject.ui.create

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.finalyearproject.databinding.ActivityCreateRecipeBinding
import com.example.finalyearproject.databinding.FragmentStep1BasicsBinding
import com.example.finalyearproject.databinding.FragmentStep2IngredientsBinding
import com.example.finalyearproject.databinding.FragmentStep3DetailsBinding
import com.example.finalyearproject.utils.Resource
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CreateRecipeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateRecipeBinding
    private val viewModel: CreateRecipeViewModel by viewModels()

    private var currentStep = 0
    private val totalSteps  = 3

    // Step fragment references — accessed via ViewPager2 adapter
    private lateinit var step1: Step1Fragment
    private lateinit var step2: Step2Fragment
    private lateinit var step3: Step3Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        step1 = Step1Fragment(viewModel)
        step2 = Step2Fragment()
        step3 = Step3Fragment()

        binding.vpSteps.adapter = StepAdapter()
        binding.vpSteps.isUserInputEnabled = false

        updateProgress()
        setupButtons()
        observeViewModel()
    }

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            if (validateCurrentStep()) {
                if (currentStep < totalSteps - 1) {
                    currentStep++
                    binding.vpSteps.setCurrentItem(currentStep, true)
                    updateProgress()
                } else {
                    submitRecipe()
                }
            }
        }

        binding.btnBackStep.setOnClickListener {
            if (currentStep > 0) {
                currentStep--
                binding.vpSteps.setCurrentItem(currentStep, true)
                updateProgress()
            }
        }
    }

    private fun updateProgress() {
        val pct = ((currentStep + 1) * 100) / totalSteps
        binding.progressStep.progress = pct
        binding.btnBackStep.visibility = if (currentStep > 0) View.VISIBLE else View.GONE
        binding.btnNext.text = if (currentStep == totalSteps - 1) "Publish ✓" else "Next →"
        supportActionBar?.title = when (currentStep) {
            0 -> "Create Recipe"
            1 -> "Ingredients & Steps"
            else -> "Final Details"
        }
    }

    private fun validateCurrentStep(): Boolean = when (currentStep) {
        0 -> {
            val title = step1.getTitle()
            if (title.isBlank()) {
                step1.setTitleError("Title is required")
                false
            } else true
        }
        1 -> {
            val ings = step2.getIngredients()
            if (ings.all { it.isBlank() }) {
                Toast.makeText(this, "Add at least one ingredient", Toast.LENGTH_SHORT).show()
                false
            } else true
        }
        2 -> {
            val ct = step3.getCookTime()
            if (ct <= 0) {
                Toast.makeText(this, "Enter cooking time in minutes", Toast.LENGTH_SHORT).show()
                false
            } else true
        }
        else -> true
    }

    private fun submitRecipe() {
        viewModel.submitRecipe(
            title       = step1.getTitle(),
            description = step1.getDescription(),
            ingredients = step2.getIngredients(),
            steps       = step2.getSteps(),
            category    = step3.getCategory(),
            cookTime    = step3.getCookTime(),
            videoUrl    = step3.getVideoUrl()
        )
    }

    private fun observeViewModel() {
        viewModel.createState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.btnNext.isEnabled = false
                    binding.btnNext.text = "Publishing…"
                }
                is Resource.Success -> {
                    Toast.makeText(this, "Recipe published! 🎉", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                is Resource.Error -> {
                    binding.btnNext.isEnabled = true
                    binding.btnNext.text = "Publish ✓"
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private inner class StepAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = totalSteps
        override fun createFragment(position: Int): Fragment = when (position) {
            0    -> step1
            1    -> step2
            else -> step3
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Step 1: Image + Title + Description
// ─────────────────────────────────────────────────────────────────────────────

class Step1Fragment(private val viewModel: CreateRecipeViewModel) : Fragment() {

    private var _b: FragmentStep1BasicsBinding? = null
    private val b get() = _b!!

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        viewModel.selectedImageUri = uri
        b.ivPreview.setImageURI(uri)
        b.ivPreview.visibility     = View.VISIBLE
        b.layoutPickHint.visibility = View.GONE
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        _b = FragmentStep1BasicsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.cardImage.setOnClickListener { pickImage.launch("image/*") }
    }

    fun getTitle()       = b.etTitle.text?.toString()?.trim() ?: ""
    fun getDescription() = b.etDescription.text?.toString()?.trim() ?: ""
    fun setTitleError(msg: String) { b.tilTitle.error = msg }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}


// ─────────────────────────────────────────────────────────────────────────────
// Step 2: Ingredients + Steps (dynamic rows)
// ─────────────────────────────────────────────────────────────────────────────

class Step2Fragment : Fragment() {

    private var _b: FragmentStep2IngredientsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        _b = FragmentStep2IngredientsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Start with 3 ingredient rows
        repeat(3) { addIngredientRow() }
        // Start with 2 step rows
        repeat(2) { addStepRow() }

        b.btnAddIngredient.setOnClickListener { addIngredientRow() }
        b.btnAddStep.setOnClickListener       { addStepRow() }
    }

    private fun addIngredientRow() {
        val et = makeInputField("Ingredient ${b.containerIngredients.childCount + 1}")
        b.containerIngredients.addView(et)
    }

    private fun addStepRow() {
        val num = b.containerSteps.childCount + 1
        val et  = makeInputField("Step $num", multiLine = true)
        b.containerSteps.addView(et)
    }

    private fun makeInputField(hint: String, multiLine: Boolean = false): TextInputLayout {
        val ctx = requireContext()
        val til = TextInputLayout(ctx,  null,
            com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox
        ).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (8 * resources.displayMetrics.density).toInt() }
            this.hint = hint
            setBoxCornerRadii(12f, 12f, 12f, 12f)
        }
        val et = TextInputEditText(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            inputType = if (multiLine)
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else
                android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setTextColor(android.graphics.Color.parseColor("#212121"))
            textSize = 14f
            setPadding(0, 40, 0, 40)
        }
        til.addView(et)
        return til
    }

    fun getIngredients(): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until b.containerIngredients.childCount) {
            val til = b.containerIngredients.getChildAt(i) as? TextInputLayout ?: continue
            result.add(((til.editText)?.text?.toString() ?: "").trim())
        }
        return result.filter { it.isNotBlank() }
    }

    fun getSteps(): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until b.containerSteps.childCount) {
            val til = b.containerSteps.getChildAt(i) as? TextInputLayout ?: continue
            result.add(((til.editText)?.text?.toString() ?: "").trim())
        }
        return result.filter { it.isNotBlank() }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}


// ─────────────────────────────────────────────────────────────────────────────
// Step 3: Category + Cook time + Video URL
// ─────────────────────────────────────────────────────────────────────────────

class Step3Fragment : Fragment() {

    private var _b: FragmentStep3DetailsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedState: Bundle?
    ): View {
        _b = FragmentStep3DetailsBinding.inflate(inflater, container, false)
        return b.root
    }

    fun getCategory(): String {
        val id = b.chipGroupCategory.checkedChipId
        return if (id == View.NO_ID) "Other"
        else b.chipGroupCategory.findViewById<Chip>(id)?.text?.toString() ?: "Other"
    }

    fun getCookTime(): Int = b.etCookTime.text?.toString()?.toIntOrNull() ?: 0
    fun getVideoUrl(): String = b.etVideoUrl.text?.toString()?.trim() ?: ""

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

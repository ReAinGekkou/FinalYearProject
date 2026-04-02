package com.example.finalyearproject.ui.create

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
import androidx.fragment.app.activityViewModels
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.vpSteps.adapter            = StepAdapter()
        binding.vpSteps.isUserInputEnabled = false

        updateProgress()
        setupButtons()
        observeViewModel()
    }

    // ── Fragment helpers ──────────────────────────────────────────────────────

    // ✅ FIX ROOT CAUSE of Bug 2:
    //
    // BEFORE (broken):
    //   fun currentFragment() = findFragmentByTag("f${vpSteps.currentItem}")
    //   fun getStep1() = currentFragment() as? Step1Fragment   ← always uses currentItem
    //   fun getStep2() = currentFragment() as? Step2Fragment   ← always uses currentItem
    //   fun getStep3() = currentFragment() as? Step3Fragment   ← always uses currentItem
    //
    // When submitRecipe() is called at step 2 (currentItem = 2):
    //   getStep1() → tag "f2" → returns Step3Fragment → cast = null → return early
    //   getStep2() → tag "f2" → returns Step3Fragment → cast = null → return early
    //   Result: submitRecipe() silently returns without publishing → nothing happens
    //   BUT validateCurrentStep() at step 2 ALSO calls getStep3() → null → returns true
    //   (vacuous true) → currentStep never increments past 2 but nothing publishes
    //   This causes the Activity to appear stuck / finish with no result.
    //
    // AFTER (fixed): each getter uses its OWN hardcoded position tag
    private fun fragmentAt(position: Int): Fragment? =
        supportFragmentManager.findFragmentByTag("f$position")

    private fun getStep1(): Step1Fragment? = fragmentAt(0) as? Step1Fragment
    private fun getStep2(): Step2Fragment? = fragmentAt(1) as? Step2Fragment
    private fun getStep3(): Step3Fragment? = fragmentAt(2) as? Step3Fragment

    // ── Buttons ───────────────────────────────────────────────────────────────

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
        binding.progressStep.progress  = pct
        binding.btnBackStep.visibility = if (currentStep > 0) View.VISIBLE else View.GONE
        binding.btnNext.text           = if (currentStep == totalSteps - 1) "Publish ✓" else "Next →"
        supportActionBar?.title = when (currentStep) {
            0    -> "Create Recipe"
            1    -> "Ingredients & Steps"
            else -> "Final Details"
        }
    }

    private fun validateCurrentStep(): Boolean = when (currentStep) {
        0 -> {
            val title = getStep1()?.getTitle() ?: ""
            if (title.isBlank()) {
                getStep1()?.setTitleError("Title is required")
                false
            } else true
        }
        1 -> {
            val ings = getStep2()?.getIngredients() ?: emptyList()
            if (ings.isEmpty()) {
                Toast.makeText(this, "Add at least one ingredient", Toast.LENGTH_SHORT).show()
                false
            } else true
        }
        2 -> {
            val ct = getStep3()?.getCookTime() ?: 0
            if (ct <= 0) {
                Toast.makeText(this, "Enter cooking time in minutes", Toast.LENGTH_SHORT).show()
                false
            } else true
        }
        else -> true
    }

    private fun submitRecipe() {
        val s1 = getStep1() ?: return
        val s2 = getStep2() ?: return
        val s3 = getStep3() ?: return
        viewModel.submitRecipe(
            title       = s1.getTitle(),
            description = s1.getDescription(),
            ingredients = s2.getIngredients(),
            steps       = s2.getSteps(),
            category    = s3.getCategory(),
            cookTime    = s3.getCookTime(),
            videoUrl    = s3.getVideoUrl()
        )
    }

    private fun observeViewModel() {
        viewModel.createState.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.btnNext.isEnabled = false
                    binding.btnNext.text      = "Publishing…"
                }
                is Resource.Success -> {
                    Toast.makeText(this, "Recipe published! 🎉", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
                is Resource.Error -> {
                    binding.btnNext.isEnabled = true
                    binding.btnNext.text      = "Publish ✓"
                    Toast.makeText(this, resource.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private inner class StepAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = totalSteps
        override fun createFragment(position: Int): Fragment = when (position) {
            0    -> Step1Fragment()
            1    -> Step2Fragment()
            else -> Step3Fragment()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step1Fragment
// ─────────────────────────────────────────────────────────────────────────────

class Step1Fragment : Fragment() {

    private var _b: FragmentStep1BasicsBinding? = null
    private val b get() = _b!!

    private val viewModel: CreateRecipeViewModel by activityViewModels()

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        viewModel.selectedImageUri = uri
        b.ivPreview.setImageURI(uri)
        b.ivPreview.visibility      = View.VISIBLE
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

    fun getTitle()                 = b.etTitle.text?.toString()?.trim() ?: ""
    fun getDescription()           = b.etDescription.text?.toString()?.trim() ?: ""
    fun setTitleError(msg: String) { b.tilTitle.error = msg }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step2Fragment
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
        repeat(3) { addIngredientRow() }
        repeat(2) { addStepRow() }
        b.btnAddIngredient.setOnClickListener { addIngredientRow() }
        b.btnAddStep.setOnClickListener       { addStepRow() }
    }

    private fun addIngredientRow() {
        b.containerIngredients.addView(
            makeInputField("Ingredient ${b.containerIngredients.childCount + 1}")
        )
    }

    private fun addStepRow() {
        b.containerSteps.addView(
            makeInputField("Step ${b.containerSteps.childCount + 1}", multiLine = true)
        )
    }

    private fun makeInputField(hint: String, multiLine: Boolean = false): TextInputLayout {
        val ctx = requireContext()
        val til = TextInputLayout(
            ctx, null,
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
            result.add((til.editText?.text?.toString() ?: "").trim())
        }
        return result.filter { it.isNotBlank() }
    }

    fun getSteps(): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until b.containerSteps.childCount) {
            val til = b.containerSteps.getChildAt(i) as? TextInputLayout ?: continue
            result.add((til.editText?.text?.toString() ?: "").trim())
        }
        return result.filter { it.isNotBlank() }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step3Fragment
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

    fun getCookTime(): Int    = b.etCookTime.text?.toString()?.toIntOrNull() ?: 0
    fun getVideoUrl(): String = b.etVideoUrl.text?.toString()?.trim() ?: ""

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
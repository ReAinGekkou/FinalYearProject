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

    private val TOTAL_STEPS = 3
    private var currentStep = 0      // 0-based index

    // Step fragment instances — created once, reused by ViewPager2
    private val step1 by lazy { Step1Fragment(viewModel) }
    private val step2 by lazy { Step2Fragment() }
    private val step3 by lazy { Step3Fragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Restore step index across config changes
        currentStep = savedInstanceState?.getInt(KEY_STEP) ?: 0

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            // Back arrow: go to previous step OR finish if on step 1
            if (currentStep > 0) goToStep(currentStep - 1)
            else finish()
        }

        setupViewPager()
        setupButtons()
        observeViewModel()
        updateUi()   // apply initial state
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt(KEY_STEP, currentStep)
    }

    // ── ViewPager2 ────────────────────────────────────────────────────────────

    private fun setupViewPager() {
        binding.vpSteps.adapter = StepPagerAdapter()
        // Disable swipe — navigation is controlled by buttons only
        binding.vpSteps.isUserInputEnabled = false
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            if (!validateCurrentStep()) return@setOnClickListener

            if (currentStep < TOTAL_STEPS - 1) {
                // ── FIX: advance the ViewPager, DO NOT start an Intent ────────
                goToStep(currentStep + 1)
            } else {
                // On the last step — submit
                submitRecipe()
            }
        }

        binding.btnBackStep.setOnClickListener {
            if (currentStep > 0) goToStep(currentStep - 1)
        }
    }

    /**
     * Moves to a specific step index.
     * Only this function is allowed to change [currentStep].
     */
    private fun goToStep(step: Int) {
        currentStep = step.coerceIn(0, TOTAL_STEPS - 1)
        // setCurrentItem animates to the target page inside the SAME activity
        binding.vpSteps.setCurrentItem(currentStep, true)
        updateUi()
    }

    // ── UI state per step ─────────────────────────────────────────────────────

    private fun updateUi() {
        val progress = ((currentStep + 1) * 100) / TOTAL_STEPS
        binding.progressStep.progress = progress

        binding.btnBackStep.visibility =
            if (currentStep > 0) View.VISIBLE else View.GONE

        binding.btnNext.text = when (currentStep) {
            TOTAL_STEPS - 1 -> "Publish ✓"
            else            -> "Next →"
        }

        supportActionBar?.title = when (currentStep) {
            0    -> "Create Recipe"
            1    -> "Ingredients & Steps"
            else -> "Final Details"
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

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
            if (ings.isEmpty()) {
                Toast.makeText(this, "Add at least one ingredient", Toast.LENGTH_SHORT).show()
                false
            } else true
        }
        2 -> {
            if (step3.getCookTime() <= 0) {
                Toast.makeText(this, "Enter cooking time in minutes", Toast.LENGTH_SHORT).show()
                false
            } else true
        }
        else -> true
    }

    // ── Submit ────────────────────────────────────────────────────────────────

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

    // ── Observe result ────────────────────────────────────────────────────────

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
                    // finish() is ONLY called here — after a successful publish
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

    // ── ViewPager2 adapter ────────────────────────────────────────────────────

    private inner class StepPagerAdapter : FragmentStateAdapter(this) {
        override fun getItemCount() = TOTAL_STEPS
        override fun createFragment(position: Int): Fragment = when (position) {
            0    -> step1
            1    -> step2
            else -> step3
        }
    }

    companion object {
        private const val KEY_STEP = "current_step"
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Step 1 — Image + Title + Description
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
        b.ivPreview.visibility      = View.VISIBLE
        b.layoutPickHint.visibility = View.GONE
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _b = FragmentStep1BasicsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.cardImage.setOnClickListener { pickImage.launch("image/*") }
    }

    fun getTitle(): String       = b.etTitle.text?.toString()?.trim() ?: ""
    fun getDescription(): String = b.etDescription.text?.toString()?.trim() ?: ""
    fun setTitleError(msg: String) { b.tilTitle.error = msg }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}


// ─────────────────────────────────────────────────────────────────────────────
// Step 2 — Ingredients + Steps (dynamic rows)
// ─────────────────────────────────────────────────────────────────────────────

class Step2Fragment : Fragment() {

    private var _b: FragmentStep2IngredientsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _b = FragmentStep2IngredientsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Seed with initial rows
        repeat(3) { addIngredientRow() }
        repeat(2) { addStepRow() }

        b.btnAddIngredient.setOnClickListener { addIngredientRow() }
        b.btnAddStep.setOnClickListener       { addStepRow() }
    }

    private fun addIngredientRow() {
        b.containerIngredients.addView(
            makeInput("Ingredient ${b.containerIngredients.childCount + 1}")
        )
    }

    private fun addStepRow() {
        b.containerSteps.addView(
            makeInput("Step ${b.containerSteps.childCount + 1}", multiLine = true)
        )
    }

    private fun makeInput(hint: String, multiLine: Boolean = false): TextInputLayout {
        val ctx = requireContext()
        val til = TextInputLayout(
            ctx, null,
            com.google.android.material.R.style
                .Widget_MaterialComponents_TextInputLayout_OutlinedBox
        ).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (8 * resources.displayMetrics.density).toInt() }
            this.hint = hint
            setBoxCornerRadii(12f, 12f, 12f, 12f)
        }
        val et = TextInputEditText(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            inputType = if (multiLine)
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setTextColor(android.graphics.Color.parseColor("#212121"))
            textSize = 14f
            setPadding(0, 40, 0, 40)
        }
        til.addView(et)
        return til
    }

    fun getIngredients(): List<String> {
        return (0 until b.containerIngredients.childCount)
            .mapNotNull { i ->
                (b.containerIngredients.getChildAt(i) as? TextInputLayout)
                    ?.editText?.text?.toString()?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
    }

    fun getSteps(): List<String> {
        return (0 until b.containerSteps.childCount)
            .mapNotNull { i ->
                (b.containerSteps.getChildAt(i) as? TextInputLayout)
                    ?.editText?.text?.toString()?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
    }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}


// ─────────────────────────────────────────────────────────────────────────────
// Step 3 — Category + Cook time + Video URL
// ─────────────────────────────────────────────────────────────────────────────

class Step3Fragment : Fragment() {

    private var _b: FragmentStep3DetailsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _b = FragmentStep3DetailsBinding.inflate(inflater, container, false)
        return b.root
    }

    fun getCategory(): String {
        val checkedId = b.chipGroupCategory.checkedChipId
        return if (checkedId == View.NO_ID) "Other"
        else b.chipGroupCategory.findViewById<Chip>(checkedId)?.text?.toString() ?: "Other"
    }

    fun getCookTime(): Int  = b.etCookTime.text?.toString()?.toIntOrNull() ?: 0
    fun getVideoUrl(): String = b.etVideoUrl.text?.toString()?.trim() ?: ""

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
package com.example.finalyearproject.ui.create

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
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

    private val TOTAL_STEPS = 3
    private var currentStep = 0

    // ── FIX: Do NOT pass arguments to Fragment via constructor ────────────────
    //
    // ORIGINAL (broken):
    //   private val step1 by lazy { Step1Fragment(viewModel) }
    //
    // ROOT CAUSE — why "Next goes to Profile":
    //   FragmentStateAdapter saves and restores fragments automatically when
    //   the Activity is recreated (rotation, process death, or when Android
    //   kills the Activity while it is in the back stack — e.g. you navigate
    //   to Profile, Android low-memory-kills CreateRecipeActivity, then you
    //   navigate back). On restoration, Android recreates each fragment using
    //   its CLASS NAME via reflection, which always calls the NO-ARGUMENT
    //   constructor. Step1Fragment had no no-arg constructor (only one that
    //   takes a ViewModel), so Android threw:
    //
    //     IllegalArgumentException:
    //       "Fragment com.example...Step1Fragment does not have a
    //        default constructor"
    //
    //   This crash is NOT always shown to the user as a visible dialog.
    //   On many devices/API levels the Activity silently finishes and the
    //   system just returns you to the previous screen — which is Profile.
    //   That is exactly the symptom: pressing Next → back to Profile.
    //
    //   The viewModel reference passed via constructor was also wrong: after
    //   process restoration the Activity gets a fresh ViewModel from
    //   by viewModels() while the Step1Fragment constructor received a
    //   reference to the OLD ViewModel object that was created before the
    //   process died — they are different instances. The fragment would read
    //   stale/empty data.
    //
    // FIX:
    //   1. Remove the constructor parameter from Step1Fragment entirely.
    //   2. Step1Fragment gets the ViewModel itself via activityViewModels()
    //      which always returns the correct Activity-scoped instance,
    //      even after process restoration.
    //   3. All three step fragments are now instantiated with no-arg
    //      constructors, so Android can restore them safely.
    //
    private val step1 by lazy { Step1Fragment() }   // ← no args
    private val step2 by lazy { Step2Fragment() }
    private val step3 by lazy { Step3Fragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentStep = savedInstanceState?.getInt(KEY_STEP) ?: 0

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            if (currentStep > 0) goToStep(currentStep - 1) else finish()
        }

        setupViewPager()
        setupButtons()
        observeViewModel()
        updateUi()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt(KEY_STEP, currentStep)
    }

    private fun setupViewPager() {
        binding.vpSteps.adapter = StepPagerAdapter()
        binding.vpSteps.isUserInputEnabled = false
    }

    private fun setupButtons() {
        binding.btnNext.setOnClickListener {
            if (!validateCurrentStep()) return@setOnClickListener
            if (currentStep < TOTAL_STEPS - 1) {
                goToStep(currentStep + 1)
            } else {
                submitRecipe()
            }
        }
        binding.btnBackStep.setOnClickListener {
            if (currentStep > 0) goToStep(currentStep - 1)
        }
    }

    private fun goToStep(step: Int) {
        currentStep = step.coerceIn(0, TOTAL_STEPS - 1)
        binding.vpSteps.setCurrentItem(currentStep, true)
        updateUi()
    }

    private fun updateUi() {
        val progress = ((currentStep + 1) * 100) / TOTAL_STEPS
        binding.progressStep.progress = progress
        binding.btnBackStep.visibility =
            if (currentStep > 0) View.VISIBLE else View.GONE
        binding.btnNext.text = if (currentStep == TOTAL_STEPS - 1) "Publish ✓" else "Next →"
        supportActionBar?.title = when (currentStep) {
            0    -> "Create Recipe"
            1    -> "Ingredients & Steps"
            else -> "Final Details"
        }
    }

    private fun validateCurrentStep(): Boolean = when (currentStep) {
        0 -> {
            if (step1.getTitle().isBlank()) {
                step1.setTitleError("Title is required"); false
            } else true
        }
        1 -> {
            if (step2.getIngredients().isEmpty()) {
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
// Step 1
// ─────────────────────────────────────────────────────────────────────────────

// ── FIX: No-arg constructor + ViewModel via activityViewModels() ──────────────
//
// ORIGINAL (broken):
//   class Step1Fragment(private val viewModel: CreateRecipeViewModel) : Fragment()
//
// Android fragment restoration requires a public no-argument constructor.
// Any constructor parameter causes an IllegalArgumentException at restore time.
//
// The ViewModel is now retrieved via activityViewModels(), which:
//   • Is scoped to the Activity — the same instance the Activity uses
//   • Survives process restoration correctly
//   • Is the standard Android pattern for sharing a ViewModel between
//     an Activity and its child fragments
//
class Step1Fragment : Fragment() {   // ← no constructor parameter

    private var _b: FragmentStep1BasicsBinding? = null
    private val b get() = _b!!

    // ── FIX: get ViewModel from the Activity scope, not via constructor ────────
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
        inflater: LayoutInflater, container: ViewGroup?, state: Bundle?
    ): View {
        _b = FragmentStep1BasicsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.cardImage.setOnClickListener { pickImage.launch("image/*") }
    }

    fun getTitle(): String         = b.etTitle.text?.toString()?.trim() ?: ""
    fun getDescription(): String   = b.etDescription.text?.toString()?.trim() ?: ""
    fun setTitleError(msg: String) { b.tilTitle.error = msg }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}


// ─────────────────────────────────────────────────────────────────────────────
// Step 2 — unchanged (ClassCastException fix was already correct)
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
        val ctx     = requireContext()
        val density = resources.displayMetrics.density

        val tilParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (8 * density).toInt()
        }

        val til = TextInputLayout(
            ctx, null,
            com.google.android.material.R.style
                .Widget_MaterialComponents_TextInputLayout_OutlinedBox
        ).apply {
            layoutParams = tilParams
            this.hint    = hint
            setBoxCornerRadii(12f, 12f, 12f, 12f)
        }

        val et = TextInputEditText(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            inputType = if (multiLine)
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else
                android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setTextColor(android.graphics.Color.parseColor("#212121"))
            textSize = 14f
            setPadding(
                (16 * density).toInt(), (14 * density).toInt(),
                (16 * density).toInt(), (14 * density).toInt()
            )
        }

        til.addView(et)
        return til
    }

    fun getIngredients(): List<String> =
        (0 until b.containerIngredients.childCount).mapNotNull { i ->
            (b.containerIngredients.getChildAt(i) as? TextInputLayout)
                ?.editText?.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }

    fun getSteps(): List<String> =
        (0 until b.containerSteps.childCount).mapNotNull { i ->
            (b.containerSteps.getChildAt(i) as? TextInputLayout)
                ?.editText?.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}


// ─────────────────────────────────────────────────────────────────────────────
// Step 3 — unchanged (no bugs here)
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
        val id = b.chipGroupCategory.checkedChipId
        return if (id == View.NO_ID) "Other"
        else b.chipGroupCategory.findViewById<Chip>(id)?.text?.toString() ?: "Other"
    }

    fun getCookTime(): Int    = b.etCookTime.text?.toString()?.toIntOrNull() ?: 0
    fun getVideoUrl(): String = b.etVideoUrl.text?.toString()?.trim() ?: ""

    override fun onDestroyView() { super.onDestroyView(); _b = null }
}
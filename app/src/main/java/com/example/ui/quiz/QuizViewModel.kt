package com.example.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.models.Quiz
import com.example.data.models.QuizQuestion
import com.example.data.models.QuizResult
import com.example.data.repository.AuraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QuizViewModel(private val repository: AuraRepository = AuraRepository()) : ViewModel() {
    private val _quiz = MutableStateFlow<Quiz?>(null)
    val quiz: StateFlow<Quiz?> = _quiz.asStateFlow()

    private val _questions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val questions: StateFlow<List<QuizQuestion>> = _questions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadQuizByAssociatedId(associatedId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val quizzes = repository.getQuizzes(associatedId = associatedId)
                val q = quizzes.firstOrNull()
                if (q != null) {
                    _quiz.value = q
                    _questions.value = repository.getQuizQuestions(q.id)
                } else {
                    _quiz.value = null
                    _questions.value = emptyList()
                    _error.value = "No quiz found for this topic."
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load quiz"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun submitQuizResult(userId: String, quizId: String, score: Int, totalQuestions: Int) {
        viewModelScope.launch {
            try {
                val result = QuizResult(
                    quizId = quizId,
                    userId = userId,
                    score = score,
                    totalQuestions = totalQuestions
                )
                repository.saveQuizResult(result)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

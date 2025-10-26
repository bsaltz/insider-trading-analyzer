package com.github.bsaltz.insider.congress.house.parser

import com.github.bsaltz.insider.congress.house.model.ParseIssue

sealed class ParseResult<out T> {
    data class Success<T>(
        val data: T,
    ) : ParseResult<T>()

    data class SuccessWithWarnings<T>(
        val data: T,
        val warnings: List<ParseIssue>,
    ) : ParseResult<T>()

    data class Error(
        val errors: List<ParseIssue>,
    ) : ParseResult<Nothing>()

    val isSuccess: Boolean
        get() = this is Success || this is SuccessWithWarnings

    val isError: Boolean
        get() = this is Error

    val hasWarnings: Boolean
        get() = this is SuccessWithWarnings

    fun getDataOrNull(): T? =
        when (this) {
            is Success -> data
            is SuccessWithWarnings -> data
            is Error -> null
        }

    fun getAllIssues(): List<ParseIssue> =
        when (this) {
            is Success -> emptyList()
            is SuccessWithWarnings -> warnings
            is Error -> errors
        }

    fun warnings(): List<ParseIssue> =
        when (this) {
            is SuccessWithWarnings -> warnings
            else -> emptyList()
        }

    fun errors(): List<ParseIssue> =
        when (this) {
            is Error -> errors
            else -> emptyList()
        }

    inline fun <R> map(transform: (T) -> R): ParseResult<R> =
        when (this) {
            is Success -> Success(transform(data))
            is SuccessWithWarnings -> SuccessWithWarnings(transform(data), warnings)
            is Error -> this
        }

    inline fun onSuccess(action: (T) -> Unit): ParseResult<T> {
        when (this) {
            is Success -> action(data)
            is SuccessWithWarnings -> action(data)
            is Error -> {}
        }
        return this
    }

    inline fun onError(action: (List<ParseIssue>) -> Unit): ParseResult<T> {
        if (this is Error) {
            action(errors)
        }
        return this
    }

    inline fun onWarnings(action: (List<ParseIssue>) -> Unit): ParseResult<T> {
        if (this is SuccessWithWarnings) {
            action(warnings)
        }
        return this
    }
}

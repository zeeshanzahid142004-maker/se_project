package com.example.myapplication

fun friendlyError(e: Exception): String {
    val msg = e.message?.lowercase() ?: ""
    return when {
        msg.contains("unable to resolve") ||
        msg.contains("timeout") ||
        msg.contains("connectexception") ||
        msg.contains("httprequestexception") ->
            "Unable to connect. Please check your internet connection."

        msg.contains("invalid login") ||
        msg.contains("invalid_grant") ||
        msg.contains("jwt") ->
            "Sign in failed. Please check your credentials."

        msg.contains("pgrst116") ||
        msg.contains("no rows") ||
        msg.contains("404") ->
            "The requested data could not be found."

        msg.contains("rls") ||
        msg.contains("permission denied") ->
            "Access denied. Contact your administrator."

        msg.contains("500") ||
        msg.contains("503") ||
        msg.contains("internal server") ->
            "Server error. Please try again in a moment."

        msg.contains("insert") ||
        msg.contains("duplicate") ||
        msg.contains("23505") ->
            "Could not save. Please try again."

        else -> "Something went wrong. Please try again."
    }
}

/** Convenience overload for [Throwable] (e.g. from [runCatching]). */
fun friendlyError(e: Throwable): String =
    friendlyError(e as? Exception ?: Exception(e.message, e))

package com.agolock.identity.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.io.Serializable
import java.util.UUID

@Suppress("SerialVersionUIDInSerializableClass")
data class AppUserPrincipal(
    val userId: UUID,
    val email: String,
    val displayName: String,
    val spaceId: UUID,
) : UserDetails,
    Serializable {
    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_USER"))

    override fun getPassword(): String = ""

    override fun getUsername(): String = userId.toString()

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = true

    companion object {
        const val serialVersionUID: Long = 1L

        fun from(authResult: com.agolock.identity.service.AuthResult): AppUserPrincipal =
            AppUserPrincipal(
                userId = authResult.user.id,
                email = authResult.user.email,
                displayName = authResult.user.displayName,
                spaceId = authResult.space.id,
            )
    }
}

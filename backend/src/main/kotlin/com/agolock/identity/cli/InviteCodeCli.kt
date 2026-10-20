package com.agolock.identity.cli

import com.agolock.identity.service.InviteCodeService
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.system.exitProcess

@Component
@Profile("cli")
class InviteCodeCli(
    private val inviteCodeService: InviteCodeService,
    private val clock: Clock,
    private val applicationContext: ConfigurableApplicationContext,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val nonOptions = args.nonOptionArgs
        if (nonOptions.size < 2 || nonOptions[0] != "invite-code") {
            printUsage()
            shutdown(2)
            return
        }

        try {
            when (nonOptions[1]) {
                "create" -> create(args)
                "revoke" -> revoke(args)
                "list" -> list()
                else -> {
                    printUsage()
                    shutdown(2)
                }
            }
        } catch (exception: IllegalArgumentException) {
            System.err.println("FAIL: ${exception.message}")
            shutdown(2)
        }
        shutdown(0)
    }

    private fun create(args: ApplicationArguments) {
        val label = args.getOptionValues("label")?.firstOrNull()
        val maxUses = args.getOptionValues("max-uses")?.firstOrNull()?.toIntOrNull() ?: 1
        val expiresIn = parseDuration(args.getOptionValues("expires-in")?.firstOrNull() ?: "7d")
        val created =
            inviteCodeService.create(
                label = label,
                maxUses = maxUses,
                expiresAt = OffsetDateTime.now(clock).plus(expiresIn),
                createdBy = null,
            )
        println("inviteCodeId=${created.inviteCodeId}")
        println("code=${created.rawCode}")
        println("maxUses=${created.maxUses}")
        println("expiresAt=${created.expiresAt}")
    }

    private fun revoke(args: ApplicationArguments) {
        val rawId =
            args.getOptionValues("id")?.firstOrNull()
                ?: throw IllegalArgumentException("--id가 필요합니다.")
        inviteCodeService.revoke(UUID.fromString(rawId))
        println("revoked=$rawId")
    }

    private fun list() {
        inviteCodeService.list().forEach { code ->
            println(
                "${code.id}\tlabel=${code.label ?: "-"}\t" +
                    "used=${code.usedCount}/${code.maxUses}\t" +
                    "expiresAt=${code.expiresAt ?: "-"}\t" +
                    "revokedAt=${code.revokedAt ?: "-"}",
            )
        }
    }

    private fun parseDuration(raw: String): Duration {
        val match =
            Regex("^(\\d+)([dhm])$").matchEntire(raw.trim().lowercase())
                ?: throw IllegalArgumentException("--expires-in은 7d, 24h, 30m 형식이어야 합니다.")
        val amount = match.groupValues[1].toLong()
        return when (match.groupValues[2]) {
            "d" -> Duration.ofDays(amount)
            "h" -> Duration.ofHours(amount)
            "m" -> Duration.ofMinutes(amount)
            else -> error("unreachable")
        }
    }

    private fun printUsage() {
        System.err.println(
            """
            사용법:
              ./gradlew :backend:bootRun --args='--spring.profiles.active=local,cli invite-code create --label staging --max-uses 20 --expires-in 7d'
              ./gradlew :backend:bootRun --args='--spring.profiles.active=local,cli invite-code revoke --id <uuid>'
              ./gradlew :backend:bootRun --args='--spring.profiles.active=local,cli invite-code list'
            """.trimIndent(),
        )
    }

    private fun shutdown(code: Int) {
        SpringApplication.exit(applicationContext, { code })
        exitProcess(code)
    }
}

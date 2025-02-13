package com.rarible.protocol.solana.nft.listener

import com.rarible.blockchain.scanner.block.Block
import com.rarible.blockchain.scanner.block.BlockRepository
import com.rarible.blockchain.scanner.block.BlockStatus
import com.rarible.blockchain.scanner.solana.client.SolanaClient
import com.rarible.core.test.containers.KGenericContainer
import com.rarible.core.test.ext.KafkaTest
import com.rarible.core.test.ext.MongoCleanup
import com.rarible.core.test.ext.MongoTest
import com.rarible.protocol.solana.common.repository.BalanceRepository
import com.rarible.protocol.solana.common.repository.TokenRepository
import com.rarible.protocol.solana.nft.listener.service.descriptors.SolanaProgramId
import com.rarible.protocol.solana.nft.listener.service.descriptors.SubscriberGroups
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.query.Query
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.math.pow

@MongoTest
@MongoCleanup
@ContextConfiguration(classes = [TestSolanaScannerConfiguration::class])
@SpringBootTest(
    classes = [NftListenerApplication::class],
    properties = [
        "application.environment = test",
        "spring.cloud.consul.config.enabled = false",
        "spring.cloud.service-registry.auto-registration.enabled = false",
        "spring.cloud.discovery.enabled = false",
        "logging.logstash.tcp-socket.enabled = false"
    ]
)
@ActiveProfiles("test")
@KafkaTest
@Testcontainers
abstract class AbstractBlockScannerTest {
    @Autowired
    private lateinit var mongo: ReactiveMongoOperations

    @Autowired
    private lateinit var client: SolanaClient

    @Autowired
    protected lateinit var tokenRepository: TokenRepository

    @Autowired
    protected lateinit var balanceRepository: BalanceRepository

    @Autowired
    private lateinit var repository: BlockRepository

    @BeforeEach
    fun cleanDatabase() = runBlocking<Unit> {
        mongo.remove(Query(), SubscriberGroups.SPL_TOKEN).awaitSingle()

        val slot = client.getLatestSlot()
        val currentBlock = client.getBlock(slot) ?: error("Can't get latest block")

        runCatching {
            repository.save(
                Block(
                    id = currentBlock.number,
                    hash = currentBlock.hash,
                    parentHash = currentBlock.parentHash,
                    timestamp = currentBlock.timestamp,
                    status = BlockStatus.SUCCESS
                )
            )
        }
    }

    protected fun mintNft(): String {
        val args = buildList {
            add("ts-node")
            add("/home/rarible/metaplex/js/packages/cli/src/cli-nft.ts")
            add("mint")
            add("-e")
            add("local")
            add("-u")
            add("https://gist.githubusercontent.com/enslinmike/a18bd9fa8e922d641a8a8a64ce84dea6/raw/a8298b26e47f30279a1b107f19287be4f198e21d/meta.json")
            add("--keypair")
            add("/root/.config/solana/id.json")
        }

        return processOperation(args) { it.parse(4, -1) }
    }

    protected fun getWallet(config: String? = null): String {
        val args = buildList {
            add("spl-token")
            add("address")
            config?.let { add("/root/.config/solana/$it") }
        }

        return processOperation(args) { it.parse(0, -1) }
    }

    protected fun createWallet(name: String): String {
        val args = listOf("solana-keygen", "new", "--outfile", "/root/.config/solana/$name")

        return processOperation(args) { it.parse(4, -1) }
    }

    protected fun createToken(decimals: Int?): String {
        val args = buildList {
            add("spl-token")
            add("create-token")
            decimals?.let { add("--decimals"); add("$it") }
        }

        return processOperation(args) { it.parse(0, -1) }
    }

    protected fun createAccount(token: String, wallet: String? = null): String {
        val args = buildList {
            add("spl-token")
            add("create-account")
            add(token)
            wallet?.let { add("--owner"); add(it) }
        }

        return processOperation(args) { it.parse(0, -1) }
    }

    protected fun mintToken(token: String, amount: ULong): String {
        val args = listOf("spl-token", "mint", token, "$amount")

        return processOperation(args) { it.parse(0, -1) }
    }

    protected fun burnToken(tokenAccount: String, amount: ULong): String {
        val args = listOf("spl-token", "burn", tokenAccount, "$amount")

        return processOperation(args) { it.parse(0, -1) }
    }

    protected fun transferToken(token: String, amount: ULong, wallet: String): String {
        val args = listOf("spl-token", "transfer", token, "$amount", wallet)

        return processOperation(args) { it.parse(0, -1) }
    }

    private fun <T> processOperation(args: List<String>, parser: (String) -> T): T {
        val exec = solana.execInContainer(*args.toTypedArray())

        assertEquals(0, exec.exitCode, exec.stderr)

        return parser(exec.stdout)
    }

    private fun String.parse(rowNumber: Int, colNumber: Int): String {
        val rows = split(System.lineSeparator())
        val i = (rowNumber + rows.size) % rows.size
        val cols = rows[i].split(" ")
        val j = (colNumber + cols.size) % cols.size

        return cols[j]
    }

    protected fun Int.scaleSupply(decimals: Int) = this * 10.0.pow(decimals.toDouble()).toLong()

    companion object {
        val solana: GenericContainer<*> = KGenericContainer("rarible/solana-docker")
            .withExposedPorts(8899)
            .withCommand("solana-test-validator --no-bpf-jit --limit-ledger-size=50_000_000 --bpf-program ${SolanaProgramId.TOKEN_METADATA_PROGRAM} /home/rarible/mpl_token_metadata.so")
            .waitingFor(Wait.defaultWaitStrategy())

        @BeforeAll
        @JvmStatic
        fun setUp() {
            solana.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val port = solana.getMappedPort(8899)

            registry.add("blockchain.scanner.solana.rpcApiUrl") { "http://127.0.0.1:$port" }
        }
    }
}

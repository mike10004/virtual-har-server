package io.github.mike10004.vhs.bmp;

import com.github.mike10004.nativehelper.subprocess.ProcessException;
import com.github.mike10004.nativehelper.subprocess.ProcessResult;
import com.github.mike10004.nativehelper.subprocess.ScopedProcessTracker;
import com.github.mike10004.nativehelper.subprocess.Subprocess;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import io.github.mike10004.vhs.bmp.KeystoreGenerator.CertificateGenerationException;
import io.github.mike10004.vhs.bmp.KeystoreGenerator.KeystoreType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Random;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Utility class for exporting keys.
 */
public class KeyExports {

    private static final Logger log = LoggerFactory.getLogger(KeyExports.class);

    private static final String EXEC_NAME_KEYTOOL = "keytool";
    private static final String EXEC_NAME_OPENSSL = "openssl";

    private final ExecutableConfig keytoolConfig, opensslConfig;
    private final KeystoreData onDemandSource;

    public KeyExports(KeystoreData keystoreData) {
        this.onDemandSource = requireNonNull(keystoreData);
        this.opensslConfig = ExecutableConfig.byNameOnly(EXEC_NAME_OPENSSL);
        this.keytoolConfig = ExecutableConfig.byNameOnly(EXEC_NAME_KEYTOOL);
    }

    /**
     * Creates a PKCS12-format file.
     *
     * <p>
     * See https://stackoverflow.com/questions/652916/converting-a-java-keystore-into-pem-format
     * for instructions on how to convert the .keystore file into a .pem file that can be installed
     * into browsers like Firefox.
     *
     * <p>In short, if {@code $KEYSTORE_FILE} is the file generated by this program, execute:
     * <pre>
     *     $ keytool -importkeystore -srckeystore $KEYSTORE_FILE -destkeystore temp.p12 -srcstoretype jks  -deststoretype pkcs12
     *     $ openssl pkcs12 -in temp.p12 -out exported-keystore.pem
     * </pre>
     * <p>The contents of `exported-keystore.pem` will be in PEM format.
     */
    public void convert(Path scratchDir, KeystoreType destType, File p12File) throws IOException, InterruptedException {
        File keystoreFile = File.createTempFile("AutoCertificateAndKeySource", ".keystore", scratchDir.toFile());
        try {
            Files.write(onDemandSource.keystoreBytes, keystoreFile);
            String keystorePassword = String.copyValueOf(onDemandSource.keystorePassword);
            {
                Subprocess program = keytoolConfig.subprocessBuilder()
                        .arg("-importkeystore")
                        .args("-srckeystore", keystoreFile.getAbsolutePath())
                        .args("-srcstoretype", onDemandSource.keystoreType.name().toLowerCase())
                        .args("-srcstorepass", keystorePassword)
                        .args("-destkeystore", p12File.getAbsolutePath())
                        .args("-deststoretype", destType.name().toLowerCase())
                        .args("-deststorepass", keystorePassword)
                        .build();
                executeSubprocessAndCheckResult(program, keytoolResult -> {
                    throw new CertificateGenerationException("nonzero exit from keytool or invalid pkcs12 file length: " + keytoolResult.exitCode());
                });
                if (p12File.length() <= 0) {
                    throw new CertificateGenerationException("pkcs12 file has invalid length: " + p12File.length());
                }
                log.debug("pkcs12: {} ({} bytes)%n", p12File, p12File.length());
            }
        } finally {
            FileUtils.forceDelete(keystoreFile);
        }
    }

    public void createPemFile(File pkcs12File, File pemFile) throws IOException, InterruptedException {
        String keystorePassword = String.copyValueOf(onDemandSource.keystorePassword);
        Subprocess subprocess = opensslConfig.subprocessBuilder()
                .arg(KeystoreType.PKCS12.name().toLowerCase())
                .args("-in", pkcs12File.getAbsolutePath())
                .args("-passin", "pass:" + keystorePassword)
                .args("-out", pemFile.getAbsolutePath())
                .args("-passout", "pass:")
                .build();
        ProcessResult<String, String> result = executeSubprocessAndCheckResult(subprocess, opensslResult -> {
            return new CertificateGenerationException("nonzero exit from openssl: " + opensslResult.exitCode());
        });
        log.debug("pem: {} ({} bytes)", pemFile, pemFile.length());
        if (pemFile.length() < 1) {
            System.err.println(result.content().stderr());
            throw new IOException("openssl failed to create pem file");
        }
    }

    private ProcessResult<String, String> executeSubprocessAndCheckResult(Subprocess subprocess, Function<ProcessResult<String, String>, ? extends RuntimeException> nonzeroExitReaction) throws IOException, InterruptedException {
        ProcessResult<String, String> result = Subprocesses.executeAndWait(subprocess, Charset.defaultCharset(), null);
        if (result.exitCode() != 0) {
            log.error("stderr {}", StringUtils.abbreviateMiddle(result.content().stderr(), "[...]", 256));
            log.error("stdout {}", StringUtils.abbreviateMiddle(result.content().stdout(), "[...]", 256));
            RuntimeException ex = nonzeroExitReaction.apply(result);
            if (ex != null) {
                throw ex;
            }
        }
        return result;
    }

    private static class Subprocesses {

        private Subprocesses() {}

        public static ProcessResult<String, String> executeAndWait(Subprocess subprocess, Charset charset, @Nullable ByteSource stdin) throws InterruptedException, ProcessException {
            ProcessResult<String, String> result;
            try (ScopedProcessTracker processTracker = new ScopedProcessTracker()) {
                result = subprocess.launcher(processTracker)
                        .outputStrings(charset, stdin)
                        .launch().await();
            }
            return result;
        }
    }

    public static void main(String[] args) throws Exception {
        Random random = new Random(KeyExports.class.getName().hashCode());
        MemorySecurityProviderTool securityProviderTool = new MemorySecurityProviderTool();
        KeystoreGenerator generator = new KeystoreGenerator(KeystoreType.JKS, securityProviderTool, random);
        KeystoreData keystoreData = generator.generate();
        Path outputDir = java.nio.file.Files.createTempDirectory("key-exports");
        KeyExports exports = new KeyExports(keystoreData);
        File p12File = outputDir.resolve("keystore-keytool.pkcs12").toFile();
        exports.convert(outputDir, KeystoreType.PKCS12, p12File);
        File pemFile = outputDir.resolve("keystore-openssl.pem").toFile();
        exports.createPemFile(p12File, pemFile);
        // securityProviderTool.
        FileUtils.listFiles(outputDir.toFile(), null, true).forEach(file -> {
            System.out.format("%s (%d bytes)%n", file, file.length());
        });
    }
}

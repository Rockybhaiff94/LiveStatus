package com.example.livestatus;

import com.example.livestatus.admin.AdminAuthService;
import com.example.livestatus.admin.AdminStorage;
import com.example.livestatus.admin.BedrockIdentityHelper;
import com.example.livestatus.config.PluginConfig;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class AdminAuthServiceTest {

    @TempDir
    File tempDir;

    private AdminStorage storage;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("AdminAuthServiceTest");
        storage = new AdminStorage(tempDir, logger);
    }

    private Player createMockPlayer(UUID uuid, String name, boolean isOp, boolean hasAdminPerm) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    String mName = method.getName();
                    if (mName.equals("getUniqueId")) return uuid;
                    if (mName.equals("getName")) return name;
                    if (mName.equals("isOp")) return isOp;
                    if (mName.equals("hasPermission")) {
                        String perm = (String) args[0];
                        if (perm.equals("mcast.admin") || perm.equals("livestatus.admin")) {
                            return hasAdminPerm;
                        }
                        return false;
                    }
                    if (mName.equals("sendMessage")) return null;
                    if (mName.equals("isOnline")) return true;
                    if (mName.equals("toString")) return "MockPlayer[" + name + "," + uuid + "]";
                    if (method.getReturnType().equals(boolean.class)) return false;
                    return null;
                }
        );
    }

    private ConsoleCommandSender createMockConsole() {
        return (ConsoleCommandSender) Proxy.newProxyInstance(
                ConsoleCommandSender.class.getClassLoader(),
                new Class<?>[]{ConsoleCommandSender.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "CONSOLE";
                    if (method.getName().equals("isOp")) return true;
                    if (method.getReturnType().equals(boolean.class)) return true;
                    return null;
                }
        );
    }

    private PluginConfig createConfig(boolean opDefaultAdmin, List<String> admins) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("admin.op-default-admin", opDefaultAdmin);
        yaml.set("admin.admins", admins);
        return new PluginConfig(yaml);
    }

    @Test
    @DisplayName("Console always retains administrative access in both modes")
    void testConsoleAccess() {
        AdminAuthService authSmp = new AdminAuthService(createConfig(true, List.of()), storage, logger);
        AdminAuthService authRestricted = new AdminAuthService(createConfig(false, List.of()), storage, logger);

        CommandSender console = createMockConsole();
        assertTrue(authSmp.isAdministrator(console), "Console must have admin access in SMP mode");
        assertTrue(authRestricted.isAdministrator(console), "Console must have admin access in restricted mode");
    }

    @Test
    @DisplayName("Authorization Matrix Test: Default SMP mode (op-default-admin: true)")
    void testAuthorizationMatrixDefaultMode() {
        PluginConfig config = createConfig(true, List.of());
        AdminAuthService auth = new AdminAuthService(config, storage, logger);

        UUID opUuid = UUID.randomUUID();
        Player opPlayer = createMockPlayer(opUuid, "OpUser", true, false);

        UUID normalUuid = UUID.randomUUID();
        Player normalPlayer = createMockPlayer(normalUuid, "RegularUser", false, false);

        // Matrix Row 1: OP by default Enabled, Explicit No -> OP has access
        assertTrue(auth.isAdministrator(opPlayer), "OP player must automatically have MCast admin in default mode");

        // Matrix Row 2: OP by default Enabled, Explicit No -> Non-OP player denied
        assertFalse(auth.isAdministrator(normalPlayer), "Non-OP player must not have MCast admin");
    }

    @Test
    @DisplayName("Authorization Matrix Test: Restricted mode (op-default-admin: false)")
    void testAuthorizationMatrixRestrictedMode() {
        UUID opUuid = UUID.randomUUID();
        Player opPlayer = createMockPlayer(opUuid, "ServerOperator", true, true);

        UUID configAdminUuid = UUID.randomUUID();
        Player configAdmin = createMockPlayer(configAdminUuid, "ConfiguredBoss", false, false);

        UUID grantedAdminUuid = UUID.randomUUID();
        Player grantedAdmin = createMockPlayer(grantedAdminUuid, "GrantedOfficer", false, false);

        // Config has "ConfiguredBoss"
        PluginConfig config = createConfig(false, List.of("ConfiguredBoss"));
        AdminAuthService auth = new AdminAuthService(config, storage, logger);

        // Matrix Row 3: OP by default Disabled, Explicit No -> OP does NOT have MCast admin
        assertFalse(auth.isAdministrator(opPlayer), "Server operator must NOT have MCast admin in restricted mode");

        // Matrix Row 4: OP by default Disabled, Configured Yes -> Configured admin has access
        assertTrue(auth.isAdministrator(configAdmin), "Configured administrator in config.yml must have admin access");

        // Grant second admin via persistent storage
        auth.grantAdmin(grantedAdminUuid, "GrantedOfficer", "CONSOLE");

        // Matrix Row 5: OP by default Disabled, Persistently Granted Yes -> Granted admin has access
        assertTrue(auth.isAdministrator(grantedAdmin), "Persistently granted administrator must have admin access");

        // Matrix Row 6: Revoked -> Player loses grant-derived access
        auth.revokeAdmin(grantedAdminUuid, "GrantedOfficer");
        assertFalse(auth.isAdministrator(grantedAdmin), "Revoked administrator must lose access");
    }

    @Test
    @DisplayName("Dual-source persistence: config administrator cannot be revoked via command storage")
    void testDualSourcePersistence() {
        UUID dualUuid = UUID.randomUUID();
        Player dualAdmin = createMockPlayer(dualUuid, "DualAdmin", false, false);

        // Player is present in both config.yml and granted via storage
        PluginConfig config = createConfig(false, List.of("DualAdmin"));
        AdminAuthService auth = new AdminAuthService(config, storage, logger);

        auth.grantAdmin(dualUuid, "DualAdmin", "CONSOLE");
        assertTrue(auth.isAdministrator(dualAdmin));

        // Revoke from storage
        boolean removed = auth.revokeAdmin(dualUuid, "DualAdmin");
        assertTrue(removed, "Storage entry should be removed");

        // Player must STILL have access because they are configured in config.yml!
        assertTrue(auth.isAdministrator(dualAdmin), "Player present in config.yml must retain access when storage grant is removed");
    }

    @Test
    @DisplayName("Grants persist across reload and server restarts")
    void testGrantsPersistAcrossReloadAndRestart() {
        UUID adminUuid = UUID.randomUUID();
        String adminName = "LongTermAdmin";

        // Step 1: Grant admin in first session
        storage.grantAdmin(adminUuid, adminName, "CONSOLE");
        assertTrue(storage.isGranted(adminUuid, adminName));

        // Step 2: Simulate complete server restart (new AdminStorage reading from file)
        AdminStorage restartedStorage = new AdminStorage(tempDir, logger);
        assertTrue(restartedStorage.isGranted(adminUuid, adminName), "Grant must survive server restart");

        AdminAuthService restartedAuth = new AdminAuthService(createConfig(false, List.of()), restartedStorage, logger);
        Player player = createMockPlayer(adminUuid, adminName, false, false);
        assertTrue(restartedAuth.isAdministrator(player), "Restarted authorization service must recognize granted admin");
    }

    @Test
    @DisplayName("Bedrock and Java player identity resolution and Floodgate prefix support")
    void testBedrockAndJavaIdentityResolution() {
        // Bedrock Floodgate player with '.' prefix
        UUID bedrockUuid = UUID.fromString("00000000-0000-0000-0009-01f28b7a63d4");
        String bedrockName = ".BedrockBuilder";

        // Config lists ".BedrockBuilder"
        PluginConfig config = createConfig(false, List.of(".BedrockBuilder"));
        AdminAuthService auth = new AdminAuthService(config, storage, logger);

        Player bedrockPlayer = createMockPlayer(bedrockUuid, bedrockName, false, false);
        assertTrue(auth.isAdministrator(bedrockPlayer), "Bedrock player with prefix should match config entry");

        // Test matching with case insensitivity
        assertTrue(BedrockIdentityHelper.matches(".bedrockbuilder", bedrockUuid, bedrockName));
        assertTrue(BedrockIdentityHelper.matches(".BedrockBuilder", bedrockUuid, ".bedrockbuilder"));

        // Test UUID config entry
        PluginConfig uuidConfig = createConfig(false, List.of(bedrockUuid.toString()));
        AdminAuthService uuidAuth = new AdminAuthService(uuidConfig, storage, logger);
        assertTrue(uuidAuth.isAdministrator(bedrockPlayer), "Bedrock player should match UUID config entry");

        // Test that a different Java player without prefix doesn't accidentally collide if names differ
        UUID javaUuid = UUID.randomUUID();
        Player differentJava = createMockPlayer(javaUuid, "RandomJavaUser", false, false);
        assertFalse(auth.isAdministrator(differentJava));
    }

    @Test
    @DisplayName("Empty admin list in restricted mode does not silently fall back to OPs")
    void testEmptyAdminListDoesNotFallbackToOps() {
        PluginConfig config = createConfig(false, List.of());
        AdminAuthService auth = new AdminAuthService(config, storage, logger);

        UUID opUuid = UUID.randomUUID();
        Player op = createMockPlayer(opUuid, "MightyOp", true, true);

        assertFalse(auth.isAdministrator(op), "Empty admin list in restricted mode must NOT fall back to granting access to OPs");
    }
}

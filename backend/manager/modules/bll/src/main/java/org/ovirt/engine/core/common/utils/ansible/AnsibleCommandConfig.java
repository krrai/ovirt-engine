/*
Copyright (c) 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.ovirt.engine.core.common.utils.ansible;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.core.common.businessentities.VdsStatic;
import org.ovirt.engine.core.common.utils.ValidationUtils;
import org.ovirt.engine.core.utils.EngineLocalConfig;

/**
 * AnsibleCommandConfig stores full configuration that allows to create ansible-playbook command.
 *
 * By default:
 * 1) We don't use any cluster.
 * 2) We use verbose mode level 1 (-v).
 * 3) Playbook directory is $PREFIX/usr/share/ovirt-ansible-roles/playbooks
 * 4) Private key used is $PREFIX/etc/pki/ovirt-engine/keys/engine_id_rsa
 * 5) Log file is $PREFIX/var/log/ovirt-engine/ansible/{prefix}-{timestamp}-{playbook-name}[-{suffix}].log
 * 6) Default inventory file is used.
 */
public class AnsibleCommandConfig implements PlaybookConfig, InventoryFileConfig, LogFileConfig {

    public static final String ANSIBLE_COMMAND = "/usr/bin/ansible-playbook";

    private final AnsibleVerbosity verboseLevel;
    private final Path privateKey;
    private final String cluster;
    private final List<String> hostnames;
    private final Map<String, Object> variables;
    private final String variableFilePath;
    private final String limit;
    private final Path inventoryFile;
    private final String playbook;
    private final boolean checkMode;

    // Logging:
    private final String logFileDirectory;
    private final String logFilePrefix;
    private final String logFileSuffix;
    private final String logFileName;
    /*
     * By default Ansible logs to syslog of the host where the playbook is being executed. If this parameter is set to
     * true the logging will be done to file which you can specify by log* methods. If this parameters is set to false,
     * the logging wil be done to syslog on hosts.
     */
    private final boolean enableLogging;

    private Path playbookDir;

    // ENV variables
    private Map<String, String> envVars;

    public AnsibleCommandConfig(AnsibleVerbosity verboseLevel,
            Path privateKey,
            String cluster,
            List<String> hostnames,
            Map<String, Object> variables,
            String variableFilePath,
            String limit,
            Path inventoryFile,
            String playbook,
            boolean checkMode,
            String logFileDirectory,
            String logFilePrefix,
            String logFileSuffix,
            String logFileName,
            boolean enableLogging,
            Path playbookDir,
            Map<String, String> envVars) {
        this.verboseLevel = verboseLevel;
        this.privateKey = privateKey;
        this.cluster = cluster;
        this.hostnames = hostnames;
        this.variables = variables;
        this.variableFilePath = variableFilePath;
        this.limit = limit;
        this.inventoryFile = inventoryFile;
        this.playbook = playbook;
        this.checkMode = checkMode;
        this.logFileDirectory = logFileDirectory;
        this.logFilePrefix = logFilePrefix;
        this.logFileSuffix = logFileSuffix;
        this.logFileName = logFileName;
        this.enableLogging = enableLogging;
        this.playbookDir = playbookDir;
        this.envVars = envVars;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String playbook() {
        return playbook;
    }

    public Path inventoryFile() {
        return inventoryFile;
    }

    public Path playbookDir() {
        return playbookDir;
    }

    public List<String> hostnames() {
        return shallowCopy(hostnames);
    }

    public String cluster() {
        return cluster;
    }

    public Path privateKey() {
        return privateKey;
    }

    public String logFileDirectory() {
        return logFileDirectory;
    }

    public String logFileName() {
        return logFileName;
    }

    public String logFilePrefix() {
        return logFilePrefix;
    }

    public String logFileSuffix() {
        return logFileSuffix;
    }

    public String stdoutCallback() {
        return envVars.get(AnsibleEnvironmentConstants.ANSIBLE_STDOUT_CALLBACK);
    }

    public boolean enableLogging() {
        return enableLogging;
    }

    public AnsibleVerbosity verboseLevel() {
        return verboseLevel;
    }

    public boolean isCheckMode() {
        return checkMode;
    }

    public String limit() {
        return limit;
    }

    public Map<String, Object> variables() {
        return shallowCopy(variables);
    }

    public String variableFilePath() {
        return variableFilePath;
    }

    /**
     * @param originalList
     *            nullable allowed
     * @param <T>
     *            list item type
     * @return shallow copy of List
     */
    private <T> List<T> shallowCopy(List<T> originalList) {
        if (originalList == null) {
            return null;
        }
        final List<T> copy = new ArrayList<>(originalList.size());
        copy.addAll(originalList);
        return copy;

    }

    /**
     * @param originalMap
     *            nullable allowed
     * @param <K>
     *            map's key type
     * @param <V>
     *            map's value type
     * @return shallow copy of List
     */
    private <K, V> Map<K, V> shallowCopy(Map<K, V> originalMap) {
        if (originalMap == null) {
            return null;
        }
        return new HashMap<K, V>(originalMap);
    }

    public static class Builder {
        private AnsibleVerbosity verboseLevel;
        private Path privateKey;
        private String cluster;
        private List<String> hostnames;
        private Map<String, Object> variables;
        private String variableFilePath;
        private String limit;
        private Path inventoryFile;
        private String playbook;
        private boolean checkMode;

        // Logging:
        private String logFileDirectory;
        private String logFilePrefix;
        private String logFileSuffix;
        private String logFileName;
        /*
         * By default Ansible logs to syslog of the host where the playbook is being executed. If this parameter is set
         * to true the logging will be done to file which you can specify by log* methods. If this parameters is set to
         * false, the logging wil be done to syslog on hosts.
         */
        private boolean enableLogging;

        private EngineLocalConfig config;
        private Path playbookDir;

        // ENV variables
        private Map<String, String> envVars;

        private Builder() {
            cluster = "unspecified";
            enableLogging = true;
            envVars = new HashMap<>();
            config = EngineLocalConfig.getInstance();
            playbookDir = Paths.get(config.getUsrDir().getPath(), "playbooks");
            privateKey = Paths.get(config.getPKIDir().getPath(), "keys", "engine_id_rsa");
            variables = new HashMap<>();

            try {
                verboseLevel = AnsibleVerbosity.valueOf(
                        "LEVEL" + EngineLocalConfig.getInstance().getProperty("ANSIBLE_PLAYBOOK_VERBOSITY_LEVEL"));
            } catch (IllegalArgumentException | NullPointerException e) {
                verboseLevel = AnsibleVerbosity.LEVEL1;
            }
        }

        public Builder checkMode(boolean checkMode) {
            this.checkMode = checkMode;
            return this;
        }

        public Builder verboseLevel(AnsibleVerbosity verboseLevel) {
            this.verboseLevel = verboseLevel;
            return this;
        }

        public Builder privateKey(Path privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        public Builder inventoryFile(Path inventoryFile) {
            this.inventoryFile = inventoryFile;
            return this;
        }

        public Builder cluster(String cluster) {
            this.cluster = cluster;
            return this;
        }

        public Builder hosts(VdsStatic... hosts) {
            this.hostnames = Arrays.stream(hosts)
                    .map(h -> formatHostPort(h.getHostName(), h.getSshPort()))
                    .collect(Collectors.toList());
            return this;
        }

        public Builder hosts(VDS... hosts) {
            this.hostnames = Arrays.stream(hosts)
                    .map(h -> formatHostPort(h.getHostName(), h.getSshPort()))
                    .collect(Collectors.toList());
            return this;
        }

        protected static String formatHostPort(String host, int port) {
            return ValidationUtils.isValidIpv6(host)
                    ? String.format("[%1$s]:%2$s", host, port)
                    : String.format("%1$s:%2$s", host, port);
        }

        public Builder variable(String name, Object value) {
            this.variables.put(name, value);
            return this;
        }

        public Builder limit(String limit) {
            this.limit = limit;
            return this;
        }

        public Builder logFileDirectory(String logFileDirectory) {
            this.logFileDirectory = logFileDirectory;
            return this;
        }

        public Builder logFileName(String logFileName) {
            this.logFileName = logFileName;
            return this;
        }

        public Builder logFilePrefix(String logFilePrefix) {
            this.logFilePrefix = logFilePrefix;
            return this;
        }

        public Builder logFileSuffix(String logFileSuffix) {
            this.logFileSuffix = logFileSuffix;
            return this;
        }

        public Builder playbook(String playbook) {
            this.playbook = Paths.get(playbookDir.toString(), playbook).toString();
            return this;
        }

        public Builder variableFilePath(String variableFilePath) {
            this.variableFilePath = variableFilePath;
            return this;
        }

        public Builder stdoutCallback(String stdoutCallback) {
            this.envVars.put(AnsibleEnvironmentConstants.ANSIBLE_STDOUT_CALLBACK, stdoutCallback);
            return this;
        }

        public Builder enableLogging(boolean enableLogging) {
            this.enableLogging = enableLogging;
            return this;
        }

        public AnsibleCommandConfig build() {
            return new AnsibleCommandConfig(verboseLevel,
                    privateKey,
                    cluster,
                    hostnames,
                    variables,
                    variableFilePath,
                    limit,
                    inventoryFile,
                    playbook,
                    checkMode,
                    logFileDirectory,
                    logFilePrefix,
                    logFileSuffix,
                    logFileName,
                    enableLogging,
                    playbookDir,
                    envVars);
        }
    }
}
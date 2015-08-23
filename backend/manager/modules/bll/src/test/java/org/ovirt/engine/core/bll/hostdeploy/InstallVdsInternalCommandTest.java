package org.ovirt.engine.core.bll.hostdeploy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.ovirt.engine.core.utils.MockConfigRule.mockConfig;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mock;
import org.ovirt.engine.core.bll.BaseCommandTest;
import org.ovirt.engine.core.common.action.hostdeploy.InstallVdsParameters;
import org.ovirt.engine.core.common.businessentities.VDS;
import org.ovirt.engine.core.common.businessentities.VDSType;
import org.ovirt.engine.core.common.config.ConfigValues;
import org.ovirt.engine.core.common.errors.EngineMessage;
import org.ovirt.engine.core.compat.Guid;
import org.ovirt.engine.core.dao.VdsDao;
import org.ovirt.engine.core.utils.MockConfigRule;

public class InstallVdsInternalCommandTest extends BaseCommandTest {

    private static final String OVIRT_ISO_PREFIX = "^rhevh-(.*)\\.*\\.iso$";
    private static final String OVIRT_ISOS_REPOSITORY_PATH = "src/test/resources/ovirt-isos";
    private static final String VALID_VERSION_OVIRT_ISO_FILENAME = "rhevh-6.2-20111010.0.el6.iso";
    private static final String INVALID_VERSION_OVIRT_ISO_FILENAME = "rhevh-5.5-20111010.0.el6.iso";
    private static final String VALID_OVIRT_VERSION = "6.2";
    private static final String INVALID_OVIRT_VERSION = "5.8";
    private static final String OVIRT_NODEOS = "^rhevh.*";

    @ClassRule
    public static MockConfigRule mcr = new MockConfigRule(
            mockConfig(ConfigValues.OvirtIsoPrefix, OVIRT_ISO_PREFIX),
            mockConfig(ConfigValues.DataDir, "."),
            mockConfig(ConfigValues.oVirtISOsRepositoryPath, OVIRT_ISOS_REPOSITORY_PATH),
            mockConfig(ConfigValues.OvirtInitialSupportedIsoVersion, VALID_OVIRT_VERSION),
            mockConfig(ConfigValues.OvirtNodeOS, OVIRT_NODEOS)
            );

    @Mock
    private VdsDao vdsDao;

    private InstallVdsInternalCommand<InstallVdsParameters> createCommand(InstallVdsParameters params) {
        InstallVdsInternalCommand<InstallVdsParameters> command = spy(new InstallVdsInternalCommand<InstallVdsParameters>(params));
        doReturn(vdsDao).when(command).getVdsDao();
        return command;
    }

    @Before
    public void mockVdsDao() {
        VDS vds = new VDS();
        vds.setVdsType(VDSType.oVirtNode);
        when(vdsDao.get(any(Guid.class))).thenReturn(vds);
    }

    private static InstallVdsParameters createParameters() {
        InstallVdsParameters param = new InstallVdsParameters(Guid.newGuid());
        param.setIsReinstallOrUpgrade(true);
        return param;
    }

    private void mockVdsWithOsVersion(String osVersion) {
        VDS vds = new VDS();
        vds.setVdsType(VDSType.oVirtNode);
        vds.setHostOs(osVersion);
        when(vdsDao.get(any(Guid.class))).thenReturn(vds);
    }

    private static void assertFailsWithCanDoActionMessage
            (InstallVdsInternalCommand<InstallVdsParameters> command, EngineMessage message) {
        assertFalse(command.canDoAction());
        assertTrue(command.getReturnValue().getCanDoActionMessages().contains(message.name()));
    }

    @Test
    public void canDoActionSucceeds() {
        mockVdsWithOsVersion(VALID_OVIRT_VERSION);
        InstallVdsParameters param = createParameters();
        InstallVdsInternalCommand<InstallVdsParameters> command = createCommand(param);
        assertTrue(command.canDoAction());
    }

    @Test
    public void canDoActionFailsIHostDoesNotExists() {
        when(vdsDao.get(any(Guid.class))).thenReturn(null);
        InstallVdsParameters param = createParameters();
        InstallVdsInternalCommand<InstallVdsParameters> command = createCommand(param);
        assertFailsWithCanDoActionMessage(command, EngineMessage.ACTION_TYPE_FAILED_HOST_NOT_EXIST);
    }

}

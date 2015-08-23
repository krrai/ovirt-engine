package org.ovirt.engine.core.bll.aaa;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.ovirt.engine.api.extensions.Base;
import org.ovirt.engine.api.extensions.ExtMap;
import org.ovirt.engine.api.extensions.aaa.Acct;
import org.ovirt.engine.api.extensions.aaa.Authn;
import org.ovirt.engine.api.extensions.aaa.Authn.AuthRecord;
import org.ovirt.engine.api.extensions.aaa.Authz;
import org.ovirt.engine.api.extensions.aaa.Mapping;
import org.ovirt.engine.core.aaa.AcctUtils;
import org.ovirt.engine.core.aaa.AuthType;
import org.ovirt.engine.core.aaa.AuthenticationProfile;
import org.ovirt.engine.core.aaa.AuthenticationProfileRepository;
import org.ovirt.engine.core.aaa.AuthzUtils;
import org.ovirt.engine.core.bll.CommandBase;
import org.ovirt.engine.core.bll.MultiLevelAdministrationHandler;
import org.ovirt.engine.core.bll.utils.PermissionSubject;
import org.ovirt.engine.core.common.AuditLogType;
import org.ovirt.engine.core.common.VdcObjectType;
import org.ovirt.engine.core.common.action.LoginResult;
import org.ovirt.engine.core.common.action.LoginUserParameters;
import org.ovirt.engine.core.common.action.VdcLoginReturnValueBase;
import org.ovirt.engine.core.common.businessentities.aaa.DbUser;
import org.ovirt.engine.core.common.config.Config;
import org.ovirt.engine.core.common.config.ConfigValues;
import org.ovirt.engine.core.common.errors.EngineMessage;
import org.ovirt.engine.core.dal.dbbroker.auditloghandling.AuditLogableBase;
import org.ovirt.engine.core.extensions.mgr.ExtensionProxy;
import org.ovirt.engine.core.utils.threadpool.ThreadPoolUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class LoginBaseCommand<T extends LoginUserParameters> extends CommandBase<T> {
    protected static final Logger log = LoggerFactory.getLogger(LoginBaseCommand.class);

    private static final Map<Integer, AuditLogType> auditLogMap = new HashMap<>();
    private static final Map<Integer, EngineMessage> engineMessagesMap = new HashMap<>();

    static {
        auditLogMap.put(Authn.AuthResult.CREDENTIALS_EXPIRED, AuditLogType.USER_ACCOUNT_PASSWORD_EXPIRED);
        auditLogMap.put(Authn.AuthResult.GENERAL_ERROR, AuditLogType.USER_VDC_LOGIN_FAILED);
        auditLogMap.put(Authn.AuthResult.CREDENTIALS_INVALID, AuditLogType.AUTH_FAILED_INVALID_CREDENTIALS);
        auditLogMap.put(Authn.AuthResult.CREDENTIALS_INCORRECT, AuditLogType.AUTH_FAILED_INVALID_CREDENTIALS);
        auditLogMap.put(Authn.AuthResult.ACCOUNT_LOCKED, AuditLogType.USER_ACCOUNT_DISABLED_OR_LOCKED);
        auditLogMap.put(Authn.AuthResult.ACCOUNT_DISABLED, AuditLogType.USER_ACCOUNT_DISABLED_OR_LOCKED);
        auditLogMap.put(Authn.AuthResult.TIMED_OUT, AuditLogType.USER_ACCOUNT_DISABLED_OR_LOCKED);
        auditLogMap.put(Authn.AuthResult.ACCOUNT_EXPIRED, AuditLogType.USER_ACCOUNT_EXPIRED);

        engineMessagesMap.put(Authn.AuthResult.GENERAL_ERROR, EngineMessage.USER_FAILED_TO_AUTHENTICATE);
        engineMessagesMap.put(Authn.AuthResult.CREDENTIALS_INVALID,
                EngineMessage.USER_FAILED_TO_AUTHENTICATE_WRONG_USERNAME_OR_PASSWORD);
        engineMessagesMap.put(Authn.AuthResult.CREDENTIALS_INCORRECT,
                EngineMessage.USER_FAILED_TO_AUTHENTICATE_WRONG_USERNAME_OR_PASSWORD);
        engineMessagesMap.put(Authn.AuthResult.ACCOUNT_LOCKED, EngineMessage.USER_ACCOUNT_DISABLED);
        engineMessagesMap.put(Authn.AuthResult.ACCOUNT_DISABLED, EngineMessage.USER_ACCOUNT_DISABLED);
        engineMessagesMap.put(Authn.AuthResult.ACCOUNT_EXPIRED, EngineMessage.USER_ACCOUNT_EXPIRED);

        engineMessagesMap.put(Authn.AuthResult.TIMED_OUT, EngineMessage.USER_FAILED_TO_AUTHENTICATE_TIMED_OUT);
        engineMessagesMap.put(Authn.AuthResult.CREDENTIALS_EXPIRED, EngineMessage.USER_PASSWORD_EXPIRED);
    }

    private String engineSessionId;

    @Inject
    private SessionDataContainer sessionDataContainer;

    public LoginBaseCommand(T parameters) {
        super(parameters);
    }

    @Override
    protected VdcLoginReturnValueBase createReturnValue() {
        return new VdcLoginReturnValueBase();
    }

    @Override
    public VdcLoginReturnValueBase getReturnValue() {
        return (VdcLoginReturnValueBase) super.getReturnValue();
    }

    @Override
    public AuditLogType getAuditLogTypeValue() {
        return getSucceeded() ? AuditLogType.USER_VDC_LOGIN : AuditLogType.USER_VDC_LOGIN_FAILED;
    }

    @Override
    protected void executeCommand() {
        setActionReturnValue(getCurrentUser());
        getReturnValue().setLoginResult(LoginResult.Autheticated);
        getReturnValue().setSessionId(engineSessionId);
        // Permissions for this user might been changed since last login so
        // update his isAdmin flag accordingly
        updateUserData();
        setSucceeded(true);
    }
   @Override
    protected boolean canDoAction() {
        String user = getParameters().getLoginName();
        if (StringUtils.isEmpty(user)) {
            ExtMap authRecord = (ExtMap) getParameters().getAuthRecord();
            if (authRecord != null) {
                user = authRecord.get(AuthRecord.PRINCIPAL);
            }
        }
        String profile = getParameters().getProfileName();
        if (StringUtils.isEmpty(profile)) {
            profile = "N/A";
        }
        setUserName(String.format("%s@%s", user, profile));

        boolean result = isUserCanBeAuthenticated();
        if (! result) {
            logAutheticationFailure();
        }
        return result;
    }

    private boolean attachUserToSession(AuthenticationProfile profile, ExtMap authRecord, ExtMap principalRecord) {
        try {
            byte s[] = new byte[64];
            SecureRandom.getInstance("SHA1PRNG").nextBytes(s);
            engineSessionId = new Base64(0).encodeToString(s);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        sessionDataContainer.setUser(engineSessionId, getCurrentUser());
        sessionDataContainer.refresh(engineSessionId);
        sessionDataContainer.setProfile(engineSessionId, profile);
        sessionDataContainer.setAuthRecord(engineSessionId, authRecord);
        sessionDataContainer.setPrincipalRecord(engineSessionId, principalRecord);

        // Add the user password to the session, as it will be needed later
        // when trying to log on to virtual machines:
        if (getParameters().getPassword() != null) {
            sessionDataContainer.setPassword(engineSessionId, getParameters().getPassword());
        }

        int userSessionHardLimit = Config.<Integer> getValue(ConfigValues.UserSessionHardLimit);
        Date validTo = userSessionHardLimit != 0 ? DateUtils.addMinutes(new Date(), userSessionHardLimit) : null;
        if (authRecord.<String> get(AuthRecord.VALID_TO) != null) {
            try {
                Date fromExtension =
                        new SimpleDateFormat("yyyyMMddHHmmssX").parse(authRecord.<String> get(AuthRecord.VALID_TO));
                if (validTo != null) {
                    validTo = validTo.compareTo(fromExtension) < 0 ? validTo : fromExtension;
                } else {
                    validTo = fromExtension;
                }
            } catch (ParseException e) {
                log.warn("Error parsing AuthRecord.VALID_TO. Default VALID_TO value will be set on session: {}",
                        e.getMessage());
                log.debug("Exception", e);
            }
        }
        sessionDataContainer.setHardLimit(engineSessionId, validTo);
        return true;
    }

    protected boolean isUserCanBeAuthenticated() {
        AuthenticationProfile profile = AuthenticationProfileRepository.getInstance().getProfile(getParameters().getProfileName());
        if (profile == null) {
            log.error("Can't login because authentication profile '{}' doesn't exist.",
                    getParameters().getProfileName());
            addCanDoActionMessage(EngineMessage.USER_FAILED_TO_AUTHENTICATE);
            return false;
        }

        ExtensionProxy authnExtension = profile.getAuthn();
        ExtMap authRecord = (ExtMap) getParameters().getAuthRecord();
        int reportReason = Acct.ReportReason.PRINCIPAL_LOGIN_CREDENTIALS;
        if (getParameters().getAuthType() != null) {
            if (AuthType.NEGOTIATION == getParameters().getAuthType()) {
                reportReason = Acct.ReportReason.PRINCIPAL_LOGIN_NEGOTIATE;
            }
        }
        String loginName = null;
        if (authRecord == null) {
            reportReason = Acct.ReportReason.PRINCIPAL_LOGIN_CREDENTIALS;

            // Verify that the login name and password have been provided:
            loginName = getParameters().getLoginName();
            if (loginName == null) {
                log.error("Can't login user because no login name has been provided.");
                addCanDoActionMessage(EngineMessage.USER_FAILED_TO_AUTHENTICATE);
                return false;
            }
            String password = getParameters().getPassword();
            if (password == null) {
                log.error("Can't login user '{}' because no password has been provided.", loginName);
                return false;
            }

            if (!AuthzUtils.supportsPasswordAuthentication(authnExtension)) {
                log.error("Can't login user '{}' because the authentication profile '{}' doesn't support password"
                                + " authentication.",
                        loginName,
                        profile.getName());
                addCanDoActionMessage(EngineMessage.USER_FAILED_TO_AUTHENTICATE);
                return false;
            }
            DbUser curUser = null;
            String curPassword = null;
            if (StringUtils.isEmpty(getParameters().getSessionId())) {
                curUser = sessionDataContainer.getUser(engineSessionId, false);
                curPassword = sessionDataContainer.getPassword(engineSessionId);
            } else {
                curUser = sessionDataContainer.getUser(getParameters().getSessionId(), false);
                curPassword = sessionDataContainer.getPassword(getParameters().getSessionId());
            }
            // verify that in auto login mode , user is not taken from session.
            if (curUser != null && !StringUtils.isEmpty(curPassword)) {
                loginName = curUser.getLoginName();
                password = curPassword;
            }
            authRecord = authenticate(profile, loginName, password);
        }
        // Perform the actual authentication:
        if (authRecord == null) {
            return false;
        }

        /*
         * set principal based on what we
         * have so far
         */
        setUserName(String.format("%s@%s", authRecord.get(Authn.AuthRecord.PRINCIPAL), profile.getName()));

        ExtensionProxy mapper = profile.getMapper();
        if (mapper != null) {
            authRecord = mapper.invoke(
                    new ExtMap().mput(
                            Base.InvokeKeys.COMMAND,
                            Mapping.InvokeCommands.MAP_AUTH_RECORD
                    ).mput(
                            Authn.InvokeKeys.AUTH_RECORD,
                            authRecord
                    ),
                    true
                ).<ExtMap> get(
                    Authn.InvokeKeys.AUTH_RECORD,
                    authRecord
                );
        }

        ExtMap principalRecord = AuthzUtils.fetchPrincipalRecord(profile.getAuthz(), authRecord);
        if (principalRecord == null) {
            log.info("Can't login user '{}' with authentication profile '{}' because the user doesn't exist in the"
                            + " directory.",
                    authRecord.<String> get(Authn.AuthRecord.PRINCIPAL),
                    profile.getName());
            addCanDoActionMessage(EngineMessage.USER_MUST_EXIST_IN_DIRECTORY);
            AcctUtils.reportRecords(
                    Acct.ReportReason.PRINCIPAL_NOT_FOUND,
                    profile.getAuthzName(),
                    loginName,
                    authRecord,
                    null,
                    "Principal record was not found. User name is %1$s",
                    loginName
                    );

            return false;
        }

        // Check that the user exists in the database, if it doesn't exist then we need to add it now:
        DbUser dbUser = DirectoryUtils.mapPrincipalRecordToDbUser(AuthzUtils.getName(profile.getAuthz()), principalRecord);
        getDbUserDao().saveOrUpdate(dbUser);

        // Check login permissions. We do it here and not via the
        // getPermissionCheckSubjects mechanism, because we need the user to be logged in to
        // the system in order to perform this check. The user is indeed logged in when running every command
        // except the login command
        if (!checkUserAndGroupsAuthorization(dbUser.getId(),
                dbUser.getGroupIds(),
                getActionType().getActionGroup(),
                MultiLevelAdministrationHandler.BOTTOM_OBJECT_ID,
                VdcObjectType.Bottom,
                true)) {
            AcctUtils.reportRecords(
                    Acct.ReportReason.PRINCIPAL_LOGIN_NO_PERMISSION,
                    profile.getAuthzName(),
                    dbUser.getLoginName(),
                    authRecord,
                    principalRecord,

                    "The user %1$s is not authorized to perform login",
                    dbUser.getLoginName()
                    );
            addCanDoActionMessage(EngineMessage.USER_NOT_AUTHORIZED_TO_PERFORM_ACTION);
            return false;
        }

        // Retrieve the MLA admin status of the user.
        // This may be redundant in some use-cases, but looking forward to Single Sign On,
        // we will want this info
        boolean isAdmin = MultiLevelAdministrationHandler.isAdminUser(dbUser);
        log.debug("Checking if user '{}' is an admin, result {}", dbUser.getLoginName(), isAdmin);
        dbUser.setAdmin(isAdmin);
        setCurrentUser(dbUser);
        AcctUtils.reportRecords(
                reportReason,
                profile.getAuthzName(),
                dbUser.getLoginName(),
                authRecord,
                principalRecord,
                "User %1$s which has princnipal name %2$s logged in ",
                dbUser.getLoginName(),
                principalRecord.<String> get(Authz.PrincipalRecord.NAME)
                );

        return attachUserToSession(profile, authRecord, principalRecord);
    }

    private void logEventForUser(String userName, AuditLogType auditLogType) {
        AuditLogableBase msg = new AuditLogableBase();
        msg.setUserName(userName);
        auditLogDirector.log(msg, auditLogType);
    }

    @Override
    protected boolean isUserAuthorizedToRunAction() {
        log.debug("IsUserAutorizedToRunAction: login - no permission check");
        return true;
    }

    @Override
    public List<PermissionSubject> getPermissionCheckSubjects() {
        // Not needed for admin operations.
        return Collections.emptyList();
    }

    private void updateUserData() {
        ThreadPoolUtil.execute(new Runnable() {
            @Override
            public void run() {
                // TODO: Will look at this later.
                // DbFacade.getInstance().updateLastAdminCheckStatus(dbUser.getId());
            }
        });
    }

    protected void logAutheticationFailure() {
        AuditLogableBase logable = new AuditLogableBase();
        logable.setUserName(getUserName());
        auditLogDirector.log(logable, AuditLogType.USER_VDC_LOGIN_FAILED);
    }

    private boolean isCredentialsAuth(ExtensionProxy authnExtension) {
        return (authnExtension.getContext().<Long> get(Authn.ContextKeys.CAPABILITIES).longValue() &
                Authn.Capabilities.AUTHENTICATE_CREDENTIALS) != 0;
    }

    private ExtMap authenticate(AuthenticationProfile profile, String user, String password) {
        ExtensionProxy authnExtension = profile.getAuthn();
        ExtMap authRecord = null;

        if (isCredentialsAuth(authnExtension)) {
            ExtensionProxy mapper = profile.getMapper();
            if (mapper != null) {
                user = mapper.invoke(new ExtMap().mput(
                        Base.InvokeKeys.COMMAND,
                        Mapping.InvokeCommands.MAP_USER
                        ).mput(
                                Mapping.InvokeKeys.USER,
                                user),
                        true).<String> get(Mapping.InvokeKeys.USER, user);
            }
        }

        ExtMap outputMap = authnExtension.invoke(new ExtMap().mput(
                Base.InvokeKeys.COMMAND,
                Authn.InvokeCommands.AUTHENTICATE_CREDENTIALS
                ).mput(
                        Authn.InvokeKeys.USER,
                        user
                ).mput(
                        Authn.InvokeKeys.CREDENTIALS,
                        password
                ));

        /*
         * set principal based on what we
         * have so far
         */
        if (outputMap.get(Authn.InvokeKeys.PRINCIPAL) != null) {
            setUserName(String.format("%s@%s", outputMap.get(Authn.InvokeKeys.PRINCIPAL), profile.getName()));
        }

        int authResult = outputMap.<Integer>get(Authn.InvokeKeys.RESULT);
        if (authResult != Authn.AuthResult.SUCCESS) {
            log.info("Can't login user '{}' with authentication profile '{}' because the authentication failed.",
                    user,
                    getParameters().getProfileName());


            AuditLogType auditLogType = auditLogMap.get(authResult);
            // if found matching audit log type, and it's not general login failure audit log (which will be logged
            // anyway due to CommandBase.log)
            if (auditLogType != null && auditLogType != AuditLogType.USER_VDC_LOGIN_FAILED) {
                logEventForUser(user, auditLogType);
            }

            if (authResult == Authn.AuthResult.CREDENTIALS_EXPIRED) {
                boolean addedUserPasswordExpiredCDA = false;
                if (outputMap.<String> get(Authn.InvokeKeys.CREDENTIALS_CHANGE_URL) != null
                        && !outputMap.<String> get(Authn.InvokeKeys.CREDENTIALS_CHANGE_URL).trim().isEmpty()) {
                    addCanDoActionMessage(EngineMessage.USER_PASSWORD_EXPIRED_CHANGE_URL_PROVIDED);
                    addCanDoActionMessageVariable("URL",
                            outputMap.<String>get(Authn.InvokeKeys.CREDENTIALS_CHANGE_URL));
                    addedUserPasswordExpiredCDA = true;
                }
                if (outputMap.<String> get(Authn.InvokeKeys.USER_MESSAGE) != null
                        && !outputMap.<String> get(Authn.InvokeKeys.USER_MESSAGE).trim().isEmpty()) {
                    addCanDoActionMessage(EngineMessage.USER_PASSWORD_EXPIRED_CHANGE_MSG_PROVIDED);
                    addCanDoActionMessageVariable("MSG",
                            outputMap.<String>get(Authn.InvokeKeys.USER_MESSAGE));
                    addedUserPasswordExpiredCDA = true;
                }
                if (!addedUserPasswordExpiredCDA) {
                    addCanDoActionMessage(EngineMessage.USER_PASSWORD_EXPIRED);
                }
            } else {
                EngineMessage msg = engineMessagesMap.get(authResult);
                if (msg == null) {
                    msg = EngineMessage.USER_FAILED_TO_AUTHENTICATE;
                }
                addCanDoActionMessage(msg);
            }
        } else {
            authRecord = outputMap.<ExtMap> get(Authn.InvokeKeys.AUTH_RECORD);
        }

        return authRecord;
    }

}

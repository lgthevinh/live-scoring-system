package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.config.AccountRole;
import org.thingai.app.scoringservice.entity.config.AuthData;
import org.thingai.base.dao.Dao;

public class AuthRepository {
    private static Dao dao;

    public static void initialize(Dao daoInstance) {
        dao = daoInstance;
    }

    // AuthData CRUD
    public static AuthData insertAuthData(AuthData authData) throws Exception {
        dao.insert(authData);
        return authData;
    }

    public static AuthData updateAuthData(AuthData authData) throws Exception {
        dao.insertOrUpdate(authData);
        return authData;
    }

    public static void deleteAuthData(String username) throws Exception {
        dao.deleteByColumn(AuthData.class, "username", username);
    }

    public static AuthData[] listAuthData() throws Exception {
        return dao.readAll(AuthData.class);
    }

    public static AuthData getAuthDataById(String username) throws Exception {
        AuthData[] authDataList = dao.query(AuthData.class, new String[]{"username"}, new String[]{username});
        if (authDataList != null && authDataList.length > 0) {
            return authDataList[0];
        }
        return null;
    }

    public static boolean authDataExists(String username) throws Exception {
        AuthData[] authDataList = dao.query(AuthData.class, new String[]{"username"}, new String[]{username});
        return authDataList != null && authDataList.length > 0;
    }

    // AccountRole CRUD
    public static AccountRole insertAccountRole(AccountRole accountRole) throws Exception {
        dao.insert(accountRole);
        return accountRole;
    }

    public static AccountRole updateAccountRole(AccountRole accountRole) throws Exception {
        dao.insertOrUpdate(accountRole);
        return accountRole;
    }

    public static void deleteAccountRole(String username) throws Exception {
        dao.deleteByColumn(AccountRole.class, "username", username);
    }

    public static AccountRole[] listAccountRoles() throws Exception {
        return dao.readAll(AccountRole.class);
    }

    public static AccountRole getAccountRoleById(String username) throws Exception {
        AccountRole[] roles = dao.query(AccountRole.class, new String[]{"username"}, new String[]{username});
        if (roles != null && roles.length > 0) {
            return roles[0];
        }
        return null;
    }

    public static boolean accountRoleExists(String username) throws Exception {
        AccountRole[] roles = dao.query(AccountRole.class, new String[]{"username"}, new String[]{username});
        return roles != null && roles.length > 0;
    }
}
package org.thingai.app.scoringservice.repository;

import org.thingai.app.scoringservice.entity.config.AccountRole;
import org.thingai.app.scoringservice.entity.config.AuthData;
import org.thingai.base.dao.Dao;

public class DaoAuth {
    private final Dao dao;

    public DaoAuth(Dao dao) {
        this.dao = dao;
    }

    // AuthData CRUD
    public AuthData insertAuthData(AuthData authData) throws Exception {
        dao.insert(authData);
        return authData;
    }

    public AuthData updateAuthData(AuthData authData) throws Exception {
        dao.insertOrUpdate(authData);
        return authData;
    }

    public void deleteAuthData(String username) throws Exception {
        dao.deleteByColumn(AuthData.class, "username", username);
    }

    public AuthData[] listAuthData() throws Exception {
        return dao.readAll(AuthData.class);
    }

    public AuthData getAuthDataById(String username) throws Exception {
        AuthData[] authDataList = dao.query(AuthData.class, new String[]{"username"}, new String[]{username});
        if (authDataList != null && authDataList.length > 0) {
            return authDataList[0];
        }
        return null;
    }

    public boolean authDataExists(String username) throws Exception {
        AuthData[] authDataList = dao.query(AuthData.class, new String[]{"username"}, new String[]{username});
        return authDataList != null && authDataList.length > 0;
    }

    // AccountRole CRUD
    public AccountRole insertAccountRole(AccountRole accountRole) throws Exception {
        dao.insert(accountRole);
        return accountRole;
    }

    public AccountRole updateAccountRole(AccountRole accountRole) throws Exception {
        dao.insertOrUpdate(accountRole);
        return accountRole;
    }

    public void deleteAccountRole(String username) throws Exception {
        dao.deleteByColumn(AccountRole.class, "username", username);
    }

    public AccountRole[] listAccountRoles() throws Exception {
        return dao.readAll(AccountRole.class);
    }

    public AccountRole getAccountRoleById(String username) throws Exception {
        AccountRole[] roles = dao.query(AccountRole.class, new String[]{"username"}, new String[]{username});
        if (roles != null && roles.length > 0) {
            return roles[0];
        }
        return null;
    }

    public boolean accountRoleExists(String username) throws Exception {
        AccountRole[] roles = dao.query(AccountRole.class, new String[]{"username"}, new String[]{username});
        return roles != null && roles.length > 0;
    }
}

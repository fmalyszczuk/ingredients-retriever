package com.malyszczuk.ingredients_retriever.auth;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Ends login sessions from the outside: all of a user's sessions are found in the session store by their email. */
@Component
public class UserSessions {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public UserSessions(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** Ends every session of the user, except the one with {@code keepSessionId} (null keeps none). */
    public void endAll(String email, String keepSessionId) {
        for (String sessionId : sessions.findByPrincipalName(email).keySet()) {
            if (!sessionId.equals(keepSessionId)) {
                sessions.deleteById(sessionId);
            }
        }
    }

    /** A deleted account must not stay logged in anywhere. */
    @TransactionalEventListener
    public void onAccountDeleted(AccountDeletedEvent event) {
        endAll(event.email(), null);
    }
}

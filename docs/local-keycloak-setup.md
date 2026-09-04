# Local Keycloak and Postman setup

The API Gateway is an OAuth 2.0 resource server. It accepts only access tokens
issued by the local `book-my-event` realm and containing the audience
`book-my-event-api`.

This setup deliberately uses two OpenID Connect clients:

- `book-my-event-api` represents the protected API audience. It does not log users in.
- `book-my-event-postman` is a public client that uses Authorization Code with PKCE to log in a user without storing a client secret.

Direct Access Grants (the password grant), Implicit Flow, service accounts, and
Keycloak Authorization Services are not required for this milestone.

## 1. Start Keycloak

From the repository root:

```powershell
docker compose up -d keycloak-db keycloak
docker compose ps keycloak-db keycloak
```

Wait until both containers show `healthy`. This command starts only the identity
stack, which is useful on a memory-constrained development machine. The rest of
the project can be started later with `docker compose up -d`.

Open the Keycloak Admin Console:

- URL: `http://localhost:8091/admin/`
- Username: `admin`, unless `KEYCLOAK_ADMIN_USERNAME` is overridden
- Password: `admin_password`, unless `KEYCLOAK_ADMIN_PASSWORD` is overridden

These are local-development defaults. Do not reuse them outside local development.

## 2. Create the realm

1. Sign in to the Admin Console. The current realm will initially be `master`.
2. Open the realm selector in the upper-left corner.
3. Select **Create realm**.
4. Set **Realm name** to `book-my-event`.
5. Leave **Enabled** on and select **Create**.
6. Confirm the realm selector now shows `book-my-event` before continuing.

Do not create application users or clients in the `master` realm.

## 3. Create the API audience client

1. In the `book-my-event` realm, open **Clients** and select **Create client**.
2. Set **Client type** to `OpenID Connect`.
3. Set **Client ID** to `book-my-event-api`, then select **Next**.
4. On **Capability config**, use these values:
   - **Client authentication**: Off
   - **Authorization**: Off
   - **Standard flow**: Off
   - **Direct access grants**: Off
   - **Implicit flow**: Off
   - **Service accounts roles**: Off, if displayed
   - **OAuth 2.0 Device Authorization Grant**: Off, if displayed
   - **OIDC CIBA Grant**: Off, if displayed
   - **Standard Token Exchange**: Off, if displayed
5. Select **Next**, leave the login URL fields empty, and select **Save**.

This client is an identifier for the API in the access token's `aud` claim. The
gateway itself does not redirect users to Keycloak and does not need a client secret.

## 4. Create the Postman login client

1. Open **Clients** and select **Create client**.
2. Set **Client type** to `OpenID Connect`.
3. Set **Client ID** to `book-my-event-postman`, then select **Next**.
4. On **Capability config**, use these values:
   - **Client authentication**: Off
   - **Authorization**: Off
   - **Standard flow**: On
   - **PKCE method**: `S256`
   - **Direct access grants**: Off
   - **Implicit flow**: Off
   - **Service accounts roles**: Off, if displayed
   - **OAuth 2.0 Device Authorization Grant**: Off, if displayed
   - **OIDC CIBA Grant**: Off, if displayed
   - **Standard Token Exchange**: Off, if displayed
5. Select **Next**.
6. Set **Valid redirect URIs** to exactly:

   ```text
   https://oauth.pstmn.io/v1/browser-callback
   ```

7. Leave **Root URL**, **Home URL**, **Valid post logout redirect URIs**, and **Web origins** empty, then select **Save**.

When editing an existing client, open **Clients** → `book-my-event-postman` →
**Settings**, find **Capability config**, keep **Standard flow** enabled, set
**PKCE method** to `S256`, and save. In current Keycloak versions this setting is
not under the client's **Advanced** tab.

The redirect URI is intentionally exact; do not replace it with `*`.

## 5. Add the API audience to Postman tokens

1. Open **Client scopes** in the realm menu and select **Create client scope**.
2. Set:
   - **Name**: `book-my-event-api-audience`
   - **Protocol**: `OpenID Connect`
   - **Display on consent screen**: Off
   - **Include in token scope**: Off
3. Save the client scope and open its **Mappers** tab.
4. Select **Configure a new mapper**, then choose **Audience**.
5. Set:
   - **Name**: `book-my-event-api-audience`
   - **Included Client Audience**: `book-my-event-api`
   - **Included Custom Audience**: empty
   - **Add to ID token**: Off
   - **Add to access token**: On
   - **Add to lightweight access token**: Off, if displayed
   - **Add to token introspection**: On, if displayed
6. Save the mapper.
7. Open **Clients** → `book-my-event-postman` → **Client scopes**.
8. Select **Add client scope**, choose `book-my-event-api-audience`, select **Add**, and assign it as **Default**.

Using a default client scope makes the audience mapper apply without requiring a
custom `scope` value in every Postman authorization request.

## 6. Create a local demo user

1. Open **Users** and select **Create new user**.
2. Set **Username** to `demo.user` and leave **Enabled** on.
3. Add a fictional first name, last name, and email if desired, then select **Create**.
4. Open the user's **Credentials** tab and select **Set password**.
5. Enter a local password that is not used anywhere else.
6. Set **Temporary** to Off and confirm **Save password**.

Do not place this password in Git, `.env.example`, a realm export, screenshots, or
shared Postman data.

## 7. Check the realm before starting the gateway

Open this URL in a browser:

```text
http://localhost:8091/realms/book-my-event/.well-known/openid-configuration
```

It should return JSON whose `issuer` is:

```text
http://localhost:8091/realms/book-my-event
```

Now start the gateway from `apigateway`:

```powershell
.\mvnw.cmd spring-boot:run
```

The local defaults are:

```text
KEYCLOAK_ISSUER_URI=http://localhost:8091/realms/book-my-event
KEYCLOAK_AUDIENCE=book-my-event-api
```

If `KEYCLOAK_PORT` is changed, set `KEYCLOAK_ISSUER_URI` to the matching host URL
before starting the gateway.

## 8. Obtain an access token in Postman

At the collection level, open **Authorization** and select **OAuth 2.0**. Add the
authorization data to **Request Headers**, then configure a new token:

| Postman field | Value |
| --- | --- |
| Token Name | `BookMyEvent local user` |
| Grant Type | `Authorization Code (With PKCE)` |
| Callback URL | `https://oauth.pstmn.io/v1/browser-callback` |
| Auth URL | `http://localhost:8091/realms/book-my-event/protocol/openid-connect/auth` |
| Access Token URL | `http://localhost:8091/realms/book-my-event/protocol/openid-connect/token` |
| Client ID | `book-my-event-postman` |
| Client Secret | Leave empty |
| Scope | `openid` |
| State | Use a freshly generated, unpredictable value; do not leave it empty |
| Code Challenge Method | `SHA-256` |
| Code Verifier | Leave empty so Postman generates it |
| Client Authentication | `Send client credentials in body` |
| Authorize using browser | On |

For a convenient local `state` value, generate a new UUID for each login attempt
in PowerShell with `[guid]::NewGuid().ToString()` and paste it into the field.

Select **Get New Access Token**, sign in as `demo.user`, then select **Use Token**.
Keep Postman's token-sharing option off.

## 9. Verify the gateway security boundary

First create a `GET http://localhost:8083/api/v1/events` request with **No Auth**.
It must return `401 Unauthorized`.

Change the request to **Inherit auth from parent** so it uses the collection's
OAuth token. It should return `200 OK`. Test the other public routes the same way:

- `GET http://localhost:8083/api/v1/events/{eventId}`
- `POST http://localhost:8083/api/v1/bookings`

The booking body is:

```json
{
  "customerId": 1,
  "eventId": 1,
  "ticketCount": 2
}
```

Also verify these expectations:

- `Authorization: Bearer invalid-token` returns `401`.
- `GET http://localhost:8083/api/v1/inventory/venue/2` with a valid token returns `404`; authentication does not publish an internal route.
- `GET http://localhost:8083/v3/api-docs/swagger-config` works without a token.
- `http://localhost:8083/swagger-ui.html` works without a token. Its **Authorize** button can accept an access token copied from Postman for trying the protected APIs.

## Current security boundary

- Authentication is enforced only at API Gateway. Direct access to service ports is a local-development bypass, not a production network design.
- This milestone verifies token signature, expiry, issuer, and API audience. It does not yet implement roles or permissions.
- The authenticated Keycloak subject is not yet linked to Booking Service's `customerId`. A valid user can currently submit any existing `customerId`; identity-to-customer ownership is a later authorization change.

After this manual flow works, follow [Export and sanitize the Keycloak realm](keycloak-realm-export.md)
to create the clean-clone import file. Do not use the Admin Console's partial
export as the repository artifact.

References: [Keycloak client capabilities and PKCE](https://www.keycloak.org/docs/latest/server_admin/), [Keycloak realm import and export](https://www.keycloak.org/server/importExport), [Keycloak OIDC endpoints](https://www.keycloak.org/securing-apps/oidc-layers), and [Postman OAuth 2.0](https://learning.postman.com/docs/use/send-requests/authorization/oauth-20).

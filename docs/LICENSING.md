# IT Nexa — Licensing (vendor guide)

IT Nexa ships with a **1-year, offline, signed license**. A license file is signed with **your private key**; the
app verifies it with the **public key embedded in the WAR**. No internet or license server is needed on the
customer's side.

## How it behaves (on the customer's server)

| Period | Shown | Who can use the portal |
|---|---|---|
| Valid term (1 year) | **Nothing** to anyone | Everyone, normally |
| After expiry — 7-day grace | Banner **only to the System Administrator**: "running on grace period, N of 7 days remaining" | Everyone still works normally |
| After the 7 days (or missing / invalid / tampered) | System Administrator sees a renew notice | **Only the System Administrator can sign in**; everyone else is sent to a "temporarily unavailable" page until renewed |

Enforcement is controlled by `itsm.license.enforce` (default **true**; set to `false` only for test/demo).
Grace length comes from the license file (`graceDays`, default 7). A **clock-tamper guard** records the last time
seen in `system_setting`; winding the server clock back is detected and suspends the license.

---

## STEP 1 — create your key pair (once, ever)

From the folder with the WAR, extract its classes and run the keygen (keygen needs no libraries):

```bat
jar -xf itsm-portal.war WEB-INF\classes WEB-INF\lib
java -cp WEB-INF\classes com.nbfc.itsm.license.tools.LicenseKeygen private.key public.key
```

- **Keep `private.key` secret forever** (password manager / offline USB). It signs every license. If it leaks,
  anyone can issue licenses; if you lose it, you cannot issue new ones.
- `public.key` is not secret.

## STEP 2 — embed your public key and build the real WAR (once)

Replace the shipped placeholder key with yours, then rebuild:

```
copy public.key  src\main\resources\license\public.key
mvn -o clean package -DskipTests
```

Now only **you** (holder of `private.key`) can produce licenses this WAR accepts. Ship this WAR to customers.
> The repository ships a demo `public.key` so the build and tests work; **you must replace it** before selling,
> otherwise the demo key (whose private key no one holds) is in effect and no license can be generated.

## STEP 3 — issue a license for each customer / renewal (1 minute)

```bat
java -cp "WEB-INF\classes;WEB-INF\lib\*" com.nbfc.itsm.license.tools.LicenseGenerator ^
     --key private.key --customer "Authum Ltd" --months 12 --grace 7 --out authum.lic
```

Send `authum.lic` to the customer (email is fine — it can't be altered without breaking the signature).

## STEP 4 — the customer installs it

Either:
- **Admin → License** in the portal (System Administrator) → upload the `.lic` file, or
- drop the file at `D:\itsm-config\itsm-license.lic` (the folder set by `ITSM_CONFIG_DIR`).

Applied immediately on upload; a dropped file is picked up within ~10 minutes or at the next restart.

## Renewal (after 1 year)

Run STEP 3 again with a new `--months 12`, send the new file, customer uploads it. No redeploy, no code change.

---

## What this stops, and what it doesn't
- **Editing/forging the license file** → impossible without your private key (signature check).
- **Using one customer's license at another company** → blocked (the customer name is signed in).
- **Winding the clock back** → detected (TAMPERED).
- **A programmer rewriting the app's code on their own server** → cannot be made impossible for any on-premise
  software, only impractical. Add obfuscation (e.g. ProGuard) for more resistance, and rely on the licence clause
  in the contract. Truly tamper-proof requires SaaS/cloud or online activation.

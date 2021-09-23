# lambdaisland.exoscale

A Clojure/Babashka wrapper for the Exoscale API.

The Exoscale API is fairly straightforward to use, except that it requires
requests to be signed, which is a bit fiddly to get right.

Exoscale offers multiple APIs with different authorization requirements. This
library supports the v1 Compute API (api.exoscale.com) and the OpenAPI-based v2.
See the [Exoscale API docs for details](https://community.exoscale.com/api/)

The alternative is to shell out to the `exo` command line tool, but that doesn't
expose everything that you can do with the API.

Exoscale provides example implementations in Go and Python, here is one now that
you can use from Clojure.

## HMAC digest

We shell out to `openssl` for this. There are JDK classes for this as well, but
they are not available in Babashka. So you need `openssl` on your `$PATH`.

## Authentication

We try to look up credentials in

- `~/.config/exoscale/exoscale.toml` (same as `exo` CLI on Linux)
- `~/Library/Application Support/exoscale/exoscale.toml` (same as `exo` CLI on Mac OS X)
- `$EXOSCALE_API_KEY` / `$EXOSCALE_API_SECRET` (same as `exo` CLI)
- `$TF_VAR_exoscale_api_key` / `$TF_VAR_exoscale_secret_key` (for Terraform users)

You can also explicitly provide a `:creds [key secret]` option to any of the
request methods.

## API

```clj
(require '[lambdaisland.exoscale :as exo])

(exo/get-v1 "/compute?command=listVirtualMachines")

(exo/get-v2 "/v2/zone")
```


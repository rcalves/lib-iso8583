# lib-iso-parser

ISO 8583 message parser library built on [jPOS](https://jpos.org). It turns a packed message into immutable Java
records (MTI, bitmap, data elements 2..128 and their subelements) for:

- Generic ISO 8583:1987 (ASCII), single and dual message.
- Mastercard, single and dual message. This is an ASCII approximation of the layout: real Banknet traffic uses EBCDIC
  and a binary bitmap.

DE48 is split into tag/length/value subelements (Mastercard also exposes the Transaction Category Code as `TCC`) and
DE55 is decoded as EMV BER-TLV (`0x00`/`0xFF` padding is skipped). Sensitive values are masked in every `toString`
(see [Masking](#masking)).

## Requirements

- Java 21+.
- jPOS **2.1.10**. jPOS 3.x is compiled for Java 22+ (3.0.0) and Java 24 (3.0.1+), so it does not run on Java 21.
- No Maven install needed: the Maven Wrapper is included and verifies the SHA-256 of the Maven 3.9.16 distribution
  it downloads.

The only runtime dependencies are `org.jpos:jpos` and `jackson-databind`. All jPOS transitive dependencies (BeanShell,
SSHD, Berkeley DB JE, jdbm, snakeyaml, jline, Bouncy Castle, slf4j 1.7, ...) are excluded: they serve Q2/server
features, and `org.jpos.iso` loads none of them. The exclusion is a wildcard, so review it on every jPOS version change
(see the comment in `pom.xml`).

**License: not decided yet, do not publish.** jPOS itself is licensed under the AGPL v3 (or a commercial license from
jPOS.org), and this project has no `LICENSE` file or `<licenses>` block yet. Choose AGPL-compatible terms or obtain a
commercial jPOS license before distributing the artifact.

Vulnerability audit: **run it before publishing any artifact**, and again on every dependency change. It has not been
run yet, so the advisory status of the pinned dependencies is unknown. It is not part of the default build because it
needs network access and an NVD API key (free, request one at https://nvd.nist.gov/developers/request-an-api-key):

```powershell
$env:NVD_API_KEY = "<key>"                                          # keeps the key out of the command line
.\mvnw.cmd -Pdependency-audit verify "-DnvdApiKey=$env:NVD_API_KEY"
```

```bash
NVD_API_KEY=<key> ./mvnw -Pdependency-audit verify "-DnvdApiKey=$NVD_API_KEY"
```

The first run downloads the NVD database (several minutes). The build fails on CVSS 7 or higher; the report is
written to `target/dependency-check-report.html`. Suppressions go in `dependency-check-suppressions.xml`, each with a
reason and an `until` date.

## Build

```powershell
.\mvnw.cmd verify      # Windows
```

```bash
./mvnw verify          # Linux / macOS
```

## Input format

`parse` expects the **hex encoding of the packed wire bytes** (what usually shows up in logs). Whitespace is ignored.
For a raw ASCII message use `parseAscii`, which converts it to bytes with the configured charset (ISO-8859-1 by default).
Whitespace after the last field (for example the line break of a line copied from a log) is ignored by `parseAscii`;
any other trailing byte is rejected by both entry points.

The configured charset only governs how `parseAscii` turns its input string into bytes. It has no effect on hex input,
and jPOS always decodes field values as ISO-8859-1. Use the charset the string was decoded with: a multi-byte charset
such as UTF-8 changes the byte length of non-ASCII characters, and the message then fails framing.

DE55 (EMV) must arrive as **hex text** inside the message: the shipped templates declare it as a character field
(`IFA_LLLCHAR`). Binary EMV bytes are reported on the field as
`Malformed subelements in DE55: the value is not hex text (EMV data must be hex encoded)`.

## Usage

```java
IsoParser parser = new IsoParser();   // reuse it: thread-safe, packagers and definitions are cached

ParserModel result = parser.parse(hexMessage, CardNetworkType.MASTERCARD, MessageType.DUAL);
IsoModel message = result.message();

String mti = message.mti().value();                       // "0100"
ClassType messageClass = message.mti().messageClass();    // AUTHORIZATION
String pan = message.field(2).map(DEModel::rawValue).orElse(null);

Map<String, DEModel> de48 = message.field(48).orElseThrow().subelements();
String tcc = de48.get("TCC").rawValue();                  // Transaction Category Code
String eci = de48.get("42").rawValue();                   // Electronic Commerce Indicators

System.out.println(result);                               // PAN printed as 555555******4444
```

Network and type can also be given by name: `parser.parse(hex, "mastercard", "dual")`. The network is checked first
(an unknown one throws `IsoParserException`), then the type (an unknown one throws `InvalidTransactionTypeException`).
All library exceptions extend the unchecked `IsoParserException`.

`parser.pack(result)` packs a `ParserModel` back into hex with the packager of its network and type (raw values are
used), so `parser.pack(parser.parse(hex, network, type))` returns `hex` for any message whose bitmap is canonical. The
bitmap is rebuilt from the fields present, so a non-canonical wire bitmap is not reproduced: bit 1 set with an
all-zero secondary bitmap is packed without the secondary bitmap. The fields are identical either way.

## Errors

- **Whole message rejected** (`IsoParserException`): input that is not hex, a message jPOS cannot unpack, bytes left
  over after the last field, or an MTI that is not 4 digits. Every decimal MTI digit decodes, digits ISO leaves
  unassigned map to `RESERVED_*` constants.
- **Reported on the field** (`DEModel.errors()`, `IsoModel.errors()` for all of them): a value that violates its
  definition (character class of `n`/`an`/`ans`/`b`/`z`/`x+n`, fixed length, `maxLength`, length prefix), and DE48/DE55
  subelements that cannot be split (truncated, bad length, duplicate tag, invalid hex). The field keeps its raw value
  with no subelements and the rest of the message is still parsed. Error messages never contain the value. A field
  that fails validation is still printed according to its sensitivity (so `DE4` stays readable); one whose
  subelements cannot be split is printed as `****`, since it may hide a sensitive subelement.

```java
if (!message.errors().isEmpty()) {
    log.warn("ISO message with invalid fields: {}", message.errors());   // e.g. {55=[Malformed subelements in DE55]}
}
```

The bitmap (`IsoModel.bitmap()`) is the one read from the wire, so bit 1 and an all-zero secondary bitmap are kept as
sent.

## Masking

Masking is part of each element: `DEModel.toString` masks its own value, so a data element or subelement is masked
the same way when it is printed on its own, inside its parent, or through `subelements()`, `fields()` or any other map
view. `IsoModel` and `ParserModel` print through it. `rawValue()` and `decodedValue()` are the explicit, unmasked
accessors.

Each definition in the JSON files declares a `sensitivity`, and the parser copies it onto the element:

| `sensitivity` | Printed as | Shipped examples |
| --- | --- | --- |
| `NONE` | as is | amounts, dates other than expiry, codes, `48/42`, `55/9F02` |
| `PARTIAL` | at most the first 6 and last 4 characters, at least 6 always masked | `2`, `34`, `102`, `103` (account identification), `55/5A` |
| `FULL` | `****` | `14` (expiry), `35`, `36`, `45` (tracks), `52` (PIN), `46`, `47` and the reserved/private DEs, Mastercard `48/10`, `48/43` (UCAF), `48/66` (authentication data), `48/92` (CVC 2), EMV `56`, `57`, `5F20`, `5F24`, `99`, `9F1F`, `9F20`, `9F6B` |

Masking is fail-closed: a definition without `sensitivity`, a field or subelement tag with no definition (shown as
`name=Unknown`, including the DE48 offset prefix `TCC` of a network that does not declare it), and a field whose
subelements cannot be split are all printed as `****`. A field's value contains its subelements' data, so it is
masked with the strongest sensitivity in its subtree: a `PARTIAL` field with a `FULL` subelement is printed as `****`,
and a field that is not sensitive itself is printed as `****` as soon as any subelement is sensitive. A validation
error alone (for example a letter in DE4) does not mask the value. Sensitivity is per network: the generic `48/10` is
`NONE`, the Mastercard one is `FULL`.

`MaskingUtils.maskValue(path, value)` and `isSensitive(path)` are for callers that mask values without definitions;
parsed elements do not use them. Their reference policy is derived from the shipped definition files (generic and
Mastercard): each path gets the strongest sensitivity any shipped network declares for it (so `48/10` is `FULL`, and
`46`, `47` and every reserved/private DE are `FULL`). A path no shipped definition declares (for example `48/35`) is
returned unchanged, so prefer the parsed `DEModel.toString` whenever you have the definitions.

Templates and definitions can be overridden. A `classpath:` prefix loads from the classpath, anything else is a file path:

```java
IsoParserConfig config = IsoParserConfig.builder()
        .packagerTemplate(CardNetworkType.GENERIC, MessageType.SINGLE, "classpath:xml/my-packager.xml")
        .definitionPath(CardNetworkType.GENERIC, "/etc/iso/my-definitions.json")
        .build();
IsoParser custom = new IsoParser(config);
```

`removePackagerTemplate(network, type)` and `removeDefinitionPath(network)` drop a default, for example to restrict a
parser to one network. Each strategy loads its packager once, on first use, and keeps it (`strategy.packager()`), so
packagers are not shared between `IsoParser` instances; `IsoUtils.loadPackager` itself does not cache. Because loading
is lazy, a wrong template path or an invalid template is reported (`IsoParserException`) on the first `parse` or
`pack` of that network and type, not by `new IsoParser(config)`. Call `parser.factory().getStrategy(network,
type).packager()` at startup to fail fast.

## Encrypting sensitive fields

`CryptoService` uses AES-GCM (random 12-byte IV, 128-bit tag). The key always comes from the caller (for example a KMS
or secret manager) and is never logged:

```java
byte[] key = loadKeyFromYourSecretStore();               // 16, 24 or 32 bytes
CryptoService crypto = new CryptoService(key, "pan-v3"); // key id; or new CryptoService(key), CryptoService.fromConfig(config)

DEModel encryptedPan = crypto.encryptField(message.field(2).orElseThrow());   // AAD: "2#raw" / "2#decoded"
DEModel pan = crypto.decryptField(encryptedPan);
```

`encryptField` also encrypts every subelement (a `null` subelement entry is kept as is). The AAD is the element's
`path()` plus the role of the value, `#raw` or `#decoded` (`2#raw`, `48/92#decoded`), so a token cannot be moved to
another element, the raw and decoded tokens of one element cannot be swapped, and a subelement gets the same AAD
whether it is encrypted on its own or through its parent. To decrypt a single token by hand, pass the same AAD:
`crypto.decrypt(field.rawValue(), field.path() + "#raw")`.

Token format (version 1): `Base64(0x01 || keyIdLength || keyId || iv || ciphertext+tag)`. Version and key id are
authenticated. Without an explicit key id one is derived from the key (8 hex characters of an HMAC-SHA256, which do
not reveal the key).

Key rotation:

1. Create the new key with a new key id and encrypt everything new with it.
2. Keep the old services for decryption only, keyed by id, and route each token by its key id. The id is read from
   the token bytes, which are untrusted, so handle a miss:

   ```java
   Map<String, CryptoService> keyring = Map.of("pan-v2", oldCrypto, "pan-v3", newCrypto);

   static String decrypt(Map<String, CryptoService> keyring, String token, String aad) {
       CryptoService crypto = keyring.get(CryptoService.keyIdOf(token));   // CryptoException if malformed
       if (crypto == null) {
           throw new CryptoException("No key for the token's key id");    // unknown or retired key id
       }
       return crypto.decrypt(token, aad);
   }
   ```

   Decrypting with the wrong service fails with `Token was encrypted with key id X, not Y`.
3. Re-encrypt stored tokens with the new key, then retire the old one.

Rotate a key well before **2^32 encryptions** (NIST SP 800-38D, 8.3: the bound for random 96-bit IVs). `encryptField`
performs two encryptions per element (raw and decoded value), subelements included, so count those.

## API changes in 0.1.0

Changes made while fixing the review findings (the project had not been released):

- `DEModel` has a sixth component, `List<String> errors` (never null), plus `hasErrors()`. The five-argument
  constructor is kept and creates an element without errors. `IsoModel.errors()` collects them per field.
- Malformed DE48/DE55 subelements no longer make `parse` throw; they are reported on the field (see [Errors](#errors)).
  Trailing bytes after the last field now make it throw.
- `BitmapModel` reflects the wire bitmap instead of one rebuilt from the present fields.
- New MTI constants: `VersionType.RESERVED_3`..`RESERVED_7`, `ClassType.RESERVED_0`, `OriginType.RESERVED_6`..
  `RESERVED_9`. `fromDigit` only throws for a non-digit.
- `MaskingUtils.maskValue` takes a path (`"48/92"`) and masks more values; `maskPan` masks at least 6 characters (a
  10-character value used to come back unchanged). New `isSensitive` and `maskFully`.
- `IsoUtils.loadPackager` no longer caches; new `MessageProcessingStrategy.packager()` and `pack(IsoModel, loader)`,
  `IsoParser.pack(ParserModel)`, `IsoParserConfig.Builder.removePackagerTemplate`/`removeDefinitionPath`.
- The by-name `parse`/`getStrategy` overloads validate the network before the message type.
- Field definitions: subelement ids are validated (unique per field, `subelementTagLength` characters for
  TAG_LENGTH_VALUE, even-length hex for BER_TLV). The generic DE48 subelements are `01`..`10`.

## API changes in 0.1.0, round 2

- `DEModel` has two more components, `String path` (`"48/92"`, defaults to `id`) and `Sensitivity sensitivity`
  (defaults to `FULL`). The five- and six-argument constructors are kept; elements built with them are now fully
  masked in `toString` unless a sensitivity is given (eight-argument constructor). A subelement's `path` is set from
  its parent when the parent is constructed. New `containsSensitiveData()`.
- `DEModel.toString` masks by the element's own sensitivity instead of a global path list, so a detached subelement
  and every map view are masked. Errors alone no longer mask the value.
- New enum `com.raphati.lib.iso.type.Sensitivity` (`NONE`, `PARTIAL`, `FULL`) and `FieldDefinitionModel.sensitivity()`
  (twelfth component, `FULL` when not declared; the eleven-argument constructor is kept). Custom definition files
  without `sensitivity` are masked fully until they declare it.
- `MaskingUtils` reference list adds `14`, `102`, `103`, `48/43`, `48/66`, `55/5F24`.
- `IsoParser.parseAscii` ignores trailing whitespace; new `MessageProcessingStrategy.parseAscii(String, Charset,
  loader)` and `IsoUtils.unpack(byte[], packager, allowTrailingWhitespace)`.
- A non-hex DE55 reports one error, `Malformed subelements in DE55: the value is not hex text (EMV data must be hex
  encoded)`, instead of the type error plus `Malformed subelements in DE55`.
- `CryptoService` tokens have a version byte and a key id (**tokens from earlier builds do not decrypt**). New
  `CryptoService(byte[], String keyId)`, `keyId()`, `static keyIdOf(token)`. `encryptField` uses `DEModel.path()` as
  AAD, so the AAD of a subelement no longer depends on the entry point.
- Both DE55 definition lists name the sensitive EMV tags and a few common ones (`4F`, `50`, `8A`, `9B`, `9F06`, ...).
- New opt-in Maven profile `dependency-audit`.

## API changes in 0.1.0, round 3

- `CryptoService.encryptField`/`decryptField` bind the value's role into the AAD: `path + "#raw"` and
  `path + "#decoded"` instead of `path` alone. **Tokens produced by earlier builds of `encryptField` do not decrypt
  with `decryptField`** (no tokens had been issued). A `null` subelement entry is now kept instead of throwing
  `NullPointerException`.
- `MaskingUtils.maskValue`/`isSensitive` derive their paths from the shipped definition files instead of a hardcoded
  list, so they now also mask `46`, `47` and the reserved/private DEs (`56`..`63`, `105`..`127`; `61`..`63` because
  the generic definitions declare them `FULL`).
- `DEModel.toString` masks with the strongest sensitivity in the element's subtree, so a `PARTIAL` element with a
  `FULL` subelement prints `****`.
- The DE48 offset prefix (`TCC`) is named and masked by its subelement definition; undeclared, it is `name=Unknown`
  and `FULL`. A TAG_LENGTH_VALUE field with `subelementOffset > 0` may declare it with id `TCC` (new constant
  `FieldDefinitionModel.OFFSET_SUBELEMENT_ID`); the Mastercard definitions declare it `NONE`. Custom definitions with
  an offset must declare `TCC` to keep printing it.

## Adding a network

1. Add a constant to `CardNetworkType`.
2. Write its single and dual strategies (extend `MessageProcessingStrategy`, supply network, type and `DEFAULT_TEMPLATE`).
3. Add the jPOS `GenericPackager` XML templates under `src/main/resources/xml/` and the JSON field definitions under
   `src/main/resources/json/` (`maxLength` of each field must match the XML `length`, and every field and subelement
   should declare its `sensitivity`; anything undeclared is masked).
4. Register the default paths in `IsoParserConfig` and the strategies in `MessageProcessingFactory`.

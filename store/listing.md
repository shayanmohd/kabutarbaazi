# Play Store listing - KabutarBaazi

## App name (max 30)
KabutarBaazi

`13 characters`

## Short description (max 80)
Kabutar kharido, becho aur apne ilaake ke kabutarbaazon se judein.

`65 characters`

## Full description (max 4000)
KabutarBaazi is made for one thing: pigeon keepers. Not a general classifieds app with a birds
category bolted on, but a place built around how kabutarbaazi actually works.

BUY AND SELL
Post an ad in under a minute. Add photos, the breed, the price and your area. Buyers reach you
straight on WhatsApp, or message you inside the app if you prefer. Filter by area so you are
looking at birds you can actually go and see.

Breeds you already know are built in: Teddy, Golden, Sherazi, Lakka, Sialkoti, Kamagar, Banka,
Rampoori, Ferozpori, Kalsira, Madrasi Highflyer, Lahori, Mookee, Fantail, Jacobin, Racing Homer
and Tippler.

REELS
Short videos of your birds. Post a flight, a new jodi, a bird you are proud of. Videos are
compressed on your own phone before they upload, so it works on a normal connection and does not
eat your data.

GROUPS BY AREA
Delhi, West UP, East UP, Punjab, Haryana, Bihar, Rajasthan, MP, Maharashtra, Bengal, Hyderabad
and Pakistan each have their own group. Ask about a breed, announce an udaan, or find people
near you.

YOUR NUMBER STAYS YOURS
Your phone number is hidden. It is shown to a buyer only when they tap the WhatsApp button on
one of your ads, and every reveal is logged. Photos are stripped of location data before upload,
so an ad never tells a stranger where you live.

THREE LANGUAGES
English, हिंदी and اردو, switchable inside the app.

SAFE AND MODERATED
Every ad, reel, post, comment and profile can be reported and every user can be blocked.
Blocking hides content in both directions. Content reported by enough people is hidden
automatically while a moderator reviews it. Cruelty and protected species are not allowed, and
that rule is enforced.

FREE
No charges, no subscription, and no ads.

---

## Category
Shopping

## Contact
Email: shayanm2002@gmail.com
Privacy policy: https://shayanmohd.github.io/kabutarbaazi/privacy-policy.html

## Play Console declaration answers

| Declaration | Answer |
|---|---|
| Privacy policy URL | https://shayanmohd.github.io/kabutarbaazi/privacy-policy.html |
| App access | **Restricted.** All content is behind a login. Provide the reviewer credentials below. |
| Ads | Does **not** contain ads |
| Government apps | No |
| Financial features | "My app doesn't provide any financial features" |
| Health | "My app doesn't have any health features" |
| **Advertising ID** | **No** - the app does not use one. Google Analytics was deliberately left disabled in Firebase so this stays true. |
| Target audience | 18 and over only |
| Content rating | See IARC answers below |
| Data safety | See table below |

### IARC content rating answers
| Question | Answer |
|---|---|
| Ratings-relevant content (violence, sexuality, drugs) | No |
| Does the app let users share content | **Yes** - ads, reels, posts, comments, direct messages |
| Online / user-reachable content | **Yes** |
| Age-restricted products | No |
| Shares precise location | **No** - area is self-reported, no GPS |
| Digital goods purchase | No |
| Cash, crypto or NFT rewards | No |
| Browser or search engine | No |
| News or educational | No |

### Data safety
| Data type | Collected | Shared | Purpose | Deletable |
|---|---|---|---|---|
| Name, username | Yes | No | App functionality, account management | Yes |
| Phone number | Yes (required) | No | App functionality (WhatsApp contact), account management | Yes |
| Approximate location (self-reported area) | Yes | No | App functionality | Yes |
| Photos and videos | Yes | No | App functionality, user-generated content | Yes |
| Messages | Yes | No | App functionality | Yes |
| Device ID (FCM token) | Yes | No | App functionality (notifications) | Yes |
| Advertising ID | **No** | - | - | - |
| Precise location | **No** | - | - | - |

Encrypted in transit: yes. Users can request deletion: yes, in-app and via the web URL.

### Permissions in the shipped AAB
```
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.POST_NOTIFICATIONS
android.permission.WAKE_LOCK                        <- added by firebase-messaging
com.google.android.c2dm.permission.RECEIVE          <- added by firebase-messaging
com.socialsure.kabutarbaazi.DYNAMIC_RECEIVER_...    <- added by firebase-messaging
```
There is deliberately **no** `READ_MEDIA_IMAGES` or `READ_MEDIA_VIDEO`: photos and videos are
chosen through the Android Photo Picker, which needs no permission at all. That is what keeps
the permissions declaration this short and the Data Safety answers provable.

Verified in the release APK: no `gms/ads`, no `AppMeasurement`, no `FirebaseAnalytics`. The lone
`firebase-measurement-connector.properties` file is an inert stub shipped inside firebase-common
and does not mean Analytics is present. Google Analytics was left switched off when the Firebase
project was created, specifically so the Advertising ID answer could honestly be No.

### App access credentials for the reviewer
Login is required, so Google needs a working account. Create one before submitting and paste it
into the App Access form. Do not commit real credentials to this file.

```
Username: <demo account>
Password: <demo password>
```

## Countries
All countries (select all), with India and Pakistan as the primary markets.

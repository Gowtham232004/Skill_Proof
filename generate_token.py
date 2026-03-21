import jwt
from datetime import datetime, timedelta

secret = "7f9a2b8c1d4e6f0a9c3b5d7e8f1a2c4e6b8d9f0a1c3e5b7d9f2a4c6e8b1d3f5"
expiration_ms = 86400000

payload = {
    "sub": "1",  # user ID
    "username": "Gowtham232004",
    "role": "user",
    "iat": datetime.utcnow(),
    "exp": datetime.utcnow() + timedelta(milliseconds=expiration_ms)
}

token = jwt.encode(payload, secret, algorithm="HS256")
print(f"JWT Token:\n{token}")

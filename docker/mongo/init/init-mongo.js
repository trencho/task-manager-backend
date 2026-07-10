// Runs once, on first start of an empty data directory.
//
// Credentials come from the environment (see docker-compose.yml and .env.example).
// Nothing here may carry a literal password: this file is committed.

const appUser = process.env.MONGO_APP_USERNAME;
const appPassword = process.env.MONGO_APP_PASSWORD;

if (!appUser || !appPassword) {
    throw new Error("MONGO_APP_USERNAME and MONGO_APP_PASSWORD must be set");
}

db = db.getSiblingDB("task-manager");

db.createUser({
    user: appUser,
    pwd: appPassword,
    roles: [
        {
            role: "readWrite",
            db: "task-manager"
        }
    ],
    mechanisms: ["SCRAM-SHA-256"]
});

db.createCollection("users");
db.createCollection("tasks");

// No seeded application user. The previous seed wrote `password` in plaintext into the
// users collection, while the app authenticates through BCryptPasswordEncoder - so that
// account could never have logged in, and it left a credential sitting in the database.
// Register through POST /api/auth/signup instead, which hashes the password properly.

print("MongoDB initialization script executed successfully.");

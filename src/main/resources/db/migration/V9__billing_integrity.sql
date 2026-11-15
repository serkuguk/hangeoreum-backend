ALTER TABLE payments ADD COLUMN provider pay_provider;
UPDATE payments p SET provider = s.provider FROM subscriptions s WHERE p.subscription_id = s.id;
UPDATE payments SET provider = 'STRIPE' WHERE provider IS NULL;
ALTER TABLE payments ALTER COLUMN provider SET NOT NULL;

CREATE UNIQUE INDEX ux_payments_provider_ref ON payments(provider, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;
CREATE UNIQUE INDEX ux_subscriptions_provider_ref ON subscriptions(provider, provider_sub_id)
    WHERE provider_sub_id IS NOT NULL;

CREATE TABLE billing_webhook_events (
    provider pay_provider NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (provider, event_id)
);

ALTER TABLE payments ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE subscriptions ALTER COLUMN user_id DROP NOT NULL;
ALTER TABLE payments DROP CONSTRAINT payments_user_id_fkey;
ALTER TABLE subscriptions DROP CONSTRAINT subscriptions_user_id_fkey;
ALTER TABLE payments ADD CONSTRAINT payments_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE subscriptions ADD CONSTRAINT subscriptions_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE payments DROP CONSTRAINT payments_subscription_id_fkey;
ALTER TABLE payments ADD CONSTRAINT payments_subscription_id_fkey
    FOREIGN KEY (subscription_id) REFERENCES subscriptions(id) ON DELETE RESTRICT;
ALTER TABLE subscriptions ADD CONSTRAINT uq_subscriptions_id_user UNIQUE (id, user_id);
-- Both user references become NULL during account deletion; check after both FK actions.
ALTER TABLE payments ADD CONSTRAINT fk_payments_subscription_owner
    FOREIGN KEY (subscription_id, user_id) REFERENCES subscriptions(id, user_id)
    DEFERRABLE INITIALLY DEFERRED;

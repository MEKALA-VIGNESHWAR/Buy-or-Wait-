import pandas as pd
samples = pd.read_csv('dataset/sample_requests.csv')
profiles = pd.read_csv('dataset/financial_profiles.csv')
for idx, r in samples.iterrows():
    p = profiles[profiles['user_id']==r['user_id']].iloc[0]
    diff = p['current_available_balance'] - p['minimum_balance_to_keep'] - r['amount_safe_to_pay']
    print(f"{r['request_id']}: safe={r['amount_safe_to_pay']}, req={r['requested_amount']}, net_avail_diff={diff:.2f}")

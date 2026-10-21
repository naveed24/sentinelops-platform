# Admin account safety

Reject role/status changes that would remove the last active administrator. Lock active administrator rows within the update transaction to serialize concurrent privilege removals.

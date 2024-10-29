UPDATE transactions SET status='RECONCILED' where status='RC';

UPDATE transactions SET status='CLEARED' where status='CL';

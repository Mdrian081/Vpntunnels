import { useEffect, useState } from 'react';
import { serversRef } from '../firebase';
import { Server } from '../types';

export function useServers() {
  const [servers, setServers] = useState<Server[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const ref = serversRef();

    const onValue = ref.on('value', snapshot => {
      try {
        const data = snapshot.val() as Record<string, Omit<Server, 'key'>> | null;
        if (!data) {
          setServers([]);
          setLoading(false);
          return;
        }
        const list: Server[] = Object.entries(data)
          .map(([key, val]) => ({ ...val, key }))
          .filter(s => s.is_active)
          .sort((a, b) => (a.name > b.name ? 1 : -1));
        setServers(list);
      } catch (e) {
        setError('Server data load failed');
      } finally {
        setLoading(false);
      }
    }, err => {
      setError(err.message);
      setLoading(false);
    });

    return () => ref.off('value', onValue);
  }, []);

  return { servers, loading, error };
}

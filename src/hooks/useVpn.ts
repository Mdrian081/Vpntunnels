import { useCallback, useEffect, useState } from 'react';
import { NativeEventEmitter, NativeModules, Platform } from 'react-native';
import { Server } from '../types';
import { VpnState } from '../types';

const { VpnModule } = NativeModules;
const emitter = VpnModule ? new NativeEventEmitter(VpnModule) : null;

export function useVpn() {
  const [state, setState] = useState<VpnState>('disconnected');
  const [connectedServer, setConnectedServer] = useState<Server | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [bytesIn, setBytesIn] = useState(0);
  const [bytesOut, setBytesOut] = useState(0);

  useEffect(() => {
    if (!emitter) return;

    const stateSub = emitter.addListener('vpnStateChanged', (event: { state: VpnState; error?: string }) => {
      setState(event.state);
      if (event.error) setError(event.error);
      if (event.state === 'disconnected') {
        setConnectedServer(null);
        setBytesIn(0);
        setBytesOut(0);
      }
    });

    const trafficSub = emitter.addListener('vpnTraffic', (event: { bytesIn: number; bytesOut: number }) => {
      setBytesIn(event.bytesIn);
      setBytesOut(event.bytesOut);
    });

    return () => {
      stateSub.remove();
      trafficSub.remove();
    };
  }, []);

  const connect = useCallback(async (server: Server) => {
    if (!VpnModule) {
      setError('VPN module not available (Android only)');
      return;
    }
    setError(null);
    setState('connecting');
    setConnectedServer(server);
    try {
      await VpnModule.connect(JSON.stringify(server));
    } catch (e: any) {
      setState('error');
      setError(e.message || 'Connection failed');
      setConnectedServer(null);
    }
  }, []);

  const disconnect = useCallback(async () => {
    if (!VpnModule) return;
    setState('disconnecting');
    try {
      await VpnModule.disconnect();
    } catch (e: any) {
      setError(e.message);
    }
  }, []);

  const prepare = useCallback(async (): Promise<boolean> => {
    if (!VpnModule) return false;
    try {
      return await VpnModule.prepare();
    } catch {
      return false;
    }
  }, []);

  return {
    state,
    connectedServer,
    error,
    bytesIn,
    bytesOut,
    connect,
    disconnect,
    prepare,
    isConnected: state === 'connected',
    isConnecting: state === 'connecting' || state === 'disconnecting'
  };
}

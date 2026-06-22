import React, { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  NativeModules,
  Platform,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import ServerCard from '../components/ServerCard';
import { useServers } from '../hooks/useServers';
import { useVpn } from '../hooks/useVpn';
import { Server } from '../types';

const FILTERS = ['All', 'VLESS', 'VMess', 'Trojan', 'SSH', 'V2Ray'];

function formatBytes(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
}

export default function HomeScreen() {
  const insets = useSafeAreaInsets();
  const { servers, loading, error } = useServers();
  const { state, connectedServer, error: vpnError, bytesIn, bytesOut, connect, disconnect, prepare } = useVpn();

  const [selectedServer, setSelectedServer] = useState<Server | null>(null);
  const [filter, setFilter] = useState('All');

  const filtered = filter === 'All'
    ? servers
    : servers.filter(s => s.type.toLowerCase() === filter.toLowerCase());

  const isConnected = state === 'connected';
  const isConnecting = state === 'connecting' || state === 'disconnecting';

  useEffect(() => {
    if (vpnError) {
      Alert.alert('VPN Error', vpnError);
    }
  }, [vpnError]);

  const handleConnectPress = useCallback(async () => {
    if (isConnected || state === 'disconnecting') {
      disconnect();
      return;
    }
    if (!selectedServer) {
      Alert.alert('সার্ভার বেছে নিন', 'কানেক্ট করার আগে একটি সার্ভার সিলেক্ট করুন।');
      return;
    }
    if (Platform.OS !== 'android') {
      Alert.alert('Android only', 'VPN শুধুমাত্র Android এ কাজ করে।');
      return;
    }

    if (!NativeModules.VpnModule) {
      Alert.alert('Error', 'VPN module পাওয়া যাচ্ছে না।');
      return;
    }

    const prepared = await prepare();
    if (!prepared) {
      Alert.alert('Permission', 'VPN permission দিতে হবে।');
      return;
    }

    connect(selectedServer);
  }, [isConnected, state, selectedServer, prepare, connect, disconnect]);

  const handleServerPress = useCallback((server: Server) => {
    if (isConnected || isConnecting) return;
    setSelectedServer(prev => prev?.key === server.key ? null : server);
  }, [isConnected, isConnecting]);

  const btnColor = isConnected ? '#ef4444' : isConnecting ? '#f59e0b' : '#6366f1';
  const btnLabel = isConnecting
    ? (state === 'connecting' ? 'কানেক্ট হচ্ছে...' : 'বিচ্ছিন্ন হচ্ছে...')
    : isConnected ? 'ডিসকানেক্ট' : 'কানেক্ট';

  return (
    <View style={[styles.root, { paddingTop: insets.top }]}>
      {/* Header */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>🔒 VPN App</Text>
        <View style={[styles.statusBadge, { backgroundColor: isConnected ? '#10b981' + '22' : '#6b7280' + '22' }]}>
          <View style={[styles.statusDot, { backgroundColor: isConnected ? '#10b981' : '#6b7280' }]} />
          <Text style={[styles.statusText, { color: isConnected ? '#10b981' : '#9ca3af' }]}>
            {isConnected ? 'সংযুক্ত' : 'বিচ্ছিন্ন'}
          </Text>
        </View>
      </View>

      {/* Connect Button */}
      <View style={styles.connectSection}>
        <View style={[styles.orb, { borderColor: btnColor + '44', shadowColor: btnColor }]}>
          <TouchableOpacity
            style={[styles.connectBtn, { backgroundColor: btnColor }]}
            onPress={handleConnectPress}
            disabled={isConnecting}
            activeOpacity={0.8}
          >
            {isConnecting
              ? <ActivityIndicator color="#fff" size="large" />
              : <Text style={styles.connectIcon}>{isConnected ? '⏹' : '▶'}</Text>
            }
          </TouchableOpacity>
        </View>
        <Text style={styles.connectLabel}>{btnLabel}</Text>

        {isConnected && connectedServer && (
          <View style={styles.connectedInfo}>
            <Text style={styles.connectedName}>{connectedServer.flag} {connectedServer.name}</Text>
            <Text style={styles.trafficText}>
              ↓ {formatBytes(bytesIn)}  ↑ {formatBytes(bytesOut)}
            </Text>
          </View>
        )}

        {selectedServer && !isConnected && (
          <Text style={styles.selectedHint}>
            {selectedServer.flag} {selectedServer.name} সিলেক্ট করা আছে
          </Text>
        )}
      </View>

      {/* Filter Bar */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.filterBar}
        contentContainerStyle={styles.filterContent}
      >
        {FILTERS.map(f => (
          <TouchableOpacity
            key={f}
            style={[styles.filterBtn, filter === f && styles.filterBtnActive]}
            onPress={() => setFilter(f)}
          >
            <Text style={[styles.filterText, filter === f && styles.filterTextActive]}>{f}</Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      {/* Server List */}
      {loading ? (
        <View style={styles.center}>
          <ActivityIndicator color="#6366f1" size="large" />
          <Text style={styles.loadingText}>সার্ভার লোড হচ্ছে...</Text>
        </View>
      ) : error ? (
        <View style={styles.center}>
          <Text style={styles.errorText}>❌ {error}</Text>
        </View>
      ) : filtered.length === 0 ? (
        <View style={styles.center}>
          <Text style={styles.emptyText}>কোনো সার্ভার নেই</Text>
        </View>
      ) : (
        <FlatList
          data={filtered}
          keyExtractor={item => item.key}
          renderItem={({ item }) => (
            <ServerCard
              server={item}
              selected={selectedServer?.key === item.key || connectedServer?.key === item.key}
              vpnState={state}
              onPress={() => handleServerPress(item)}
            />
          )}
          contentContainerStyle={[styles.list, { paddingBottom: insets.bottom + 20 }]}
          showsVerticalScrollIndicator={false}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#0d0d1a',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#1a1a2e',
  },
  headerTitle: {
    color: '#f0f0ff',
    fontSize: 20,
    fontWeight: '700',
  },
  statusBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 20,
    paddingHorizontal: 10,
    paddingVertical: 5,
    gap: 6,
  },
  statusDot: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
  },
  statusText: {
    fontSize: 13,
    fontWeight: '600',
  },
  connectSection: {
    alignItems: 'center',
    paddingVertical: 30,
  },
  orb: {
    width: 120,
    height: 120,
    borderRadius: 60,
    borderWidth: 3,
    alignItems: 'center',
    justifyContent: 'center',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.5,
    shadowRadius: 20,
    elevation: 10,
    marginBottom: 4,
  },
  connectBtn: {
    width: 90,
    height: 90,
    borderRadius: 45,
    alignItems: 'center',
    justifyContent: 'center',
  },
  connectIcon: {
    fontSize: 32,
    color: '#fff',
  },
  connectLabel: {
    color: '#c4c4d4',
    fontSize: 15,
    fontWeight: '600',
    marginTop: 10,
  },
  connectedInfo: {
    marginTop: 12,
    alignItems: 'center',
  },
  connectedName: {
    color: '#10b981',
    fontSize: 14,
    fontWeight: '600',
  },
  trafficText: {
    color: '#6b7280',
    fontSize: 12,
    marginTop: 4,
    fontFamily: 'monospace',
  },
  selectedHint: {
    marginTop: 10,
    color: '#6366f1',
    fontSize: 13,
  },
  filterBar: {
    maxHeight: 48,
    marginBottom: 8,
  },
  filterContent: {
    paddingHorizontal: 16,
    gap: 8,
    alignItems: 'center',
  },
  filterBtn: {
    paddingHorizontal: 16,
    paddingVertical: 7,
    borderRadius: 20,
    backgroundColor: '#1a1a2e',
    borderWidth: 1,
    borderColor: '#2a2a3e',
  },
  filterBtnActive: {
    backgroundColor: '#6366f1',
    borderColor: '#6366f1',
  },
  filterText: {
    color: '#9ca3af',
    fontSize: 13,
    fontWeight: '600',
  },
  filterTextActive: {
    color: '#fff',
  },
  list: {
    paddingHorizontal: 16,
    paddingTop: 8,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
  },
  loadingText: {
    color: '#6b7280',
    fontSize: 14,
  },
  errorText: {
    color: '#ef4444',
    fontSize: 14,
    textAlign: 'center',
    paddingHorizontal: 20,
  },
  emptyText: {
    color: '#6b7280',
    fontSize: 15,
  },
});

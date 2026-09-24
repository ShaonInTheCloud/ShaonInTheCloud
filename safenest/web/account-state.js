// UI state only. Supabase Auth validates sessions; Postgres RLS enforces access.
export function nextAccountState(previous, event, session) {
  if (!session?.user || event === 'SIGNED_OUT') {
    return { user: null, recovering: false, view: 'login' };
  }
  const sameUser = !previous.user || previous.user.id === session.user.id;
  const recovering = event === 'PASSWORD_RECOVERY' || Boolean(previous.recovering && sameUser);
  return { user: session.user, recovering, view: recovering ? 'recovery' : 'account' };
}

export function mayShowProfile(state, requestedUserId, requestedRevision, currentRevision) {
  return state.user?.id === requestedUserId && requestedRevision === currentRevision;
}

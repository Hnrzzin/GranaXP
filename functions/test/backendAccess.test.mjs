import assert from 'node:assert/strict';
import {test} from 'node:test';
import {callableNames, requireCallableUid, maySyncAchievements} from '../lib/backendAccess.js';

test('matriz individual: cada modo controla somente sua callable e sempre exige Auth', () => {
  const old={...process.env};
  try {
    process.env.BACKEND_ACCESS_MODE='open';
    process.env.BACKEND_PILOT_UID='pilot-user';
    for(const selected of callableNames) for(const mode of ['disabled','pilot','open']) {
      process.env.BACKEND_CALLABLE_MODES=JSON.stringify(Object.fromEntries(callableNames.map(name=>[name,name===selected?mode:'disabled'])));
      for(const name of callableNames) {
        const request=uid=>({auth:uid?{uid}:undefined,data:{uid:'pilot-user',mode:'open',BACKEND_CALLABLE_MODES:'open'}});
        assert.throws(()=>requireCallableUid(name,request()),e=>e.code==='unauthenticated');
        if(name===selected && mode!=='disabled') assert.equal(requireCallableUid(name,request('pilot-user')),'pilot-user');
        else assert.throws(()=>requireCallableUid(name,request('pilot-user')),e=>e.code==='failed-precondition');
        if(name===selected && mode==='pilot') assert.throws(()=>requireCallableUid(name,request('other')),e=>e.code==='permission-denied');
      }
    }
  } finally { for(const key of ['BACKEND_CALLABLE_MODES','BACKEND_ACCESS_MODE','BACKEND_PILOT_UID']) {
    if(old[key]===undefined) delete process.env[key]; else process.env[key]=old[key];
  } }
});
test('configuração ausente, adulterada ou malformada falha fechada sem vazar conteúdo', () => {
  for(const raw of [undefined,'','{secret-marker','[]','null','{"invented":"open"}','{"openLesson":"OPEN"}']) {
    if(raw===undefined) delete process.env.BACKEND_CALLABLE_MODES; else process.env.BACKEND_CALLABLE_MODES=raw;
    for(const name of callableNames) assert.throws(()=>requireCallableUid(name,{auth:{uid:'owner'}}),e=>
      e.code==='failed-precondition' && !e.message.includes('secret-marker'));
  }
});
test('sincronização de conquistas possui contenção própria e piloto exato', () => {
  process.env.BACKEND_PILOT_UID='pilot-user';
  for(const mode of ['disabled','invalid','pilot','open']) {
    process.env.ACHIEVEMENT_SYNC_MODE=mode;
    assert.equal(maySyncAchievements('pilot-user'),mode==='pilot'||mode==='open');
    assert.equal(maySyncAchievements('other'),mode==='open');
  }
});

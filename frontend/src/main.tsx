import {useEffect, useState} from 'react';
import {createRoot} from 'react-dom/client';
import './style.css';

type Reservation = {resourceId:string; name:string; quantity:number; startsAt:string; endsAt:string};
type Booking = {id:string; roomId:string; title:string; startsAt:string; endsAt:string; totalCents:number; reservations:Reservation[]};
type Allocation = {resourceId:string; name:string; kind:string; quantity:number; priceUnits:number; unitPriceCents:number; totalCents:number; version:number; startsAt:string; endsAt:string; assignedRoomId:string|null};
type Credit = {amountCents:number; approvedBy:string; approvedAt:string};
type Proposal = {credit:Credit|null; payableTotalCents:number;id:string; roomId:string; roomName:string; totalCents:number; roomVersion:number; booking:Booking|null; allocations:Allocation[]};
type Assessment = {id:string; status:string; summary:string; openQuestions:string[]; sessionId:string|null; proposal:Proposal|null};
type Event = {id:string; title:string; eventDate:string; attendees:number; budgetCents:number; assessments:Assessment[]; eventType:'MEETING'|'WORKSHOP'; standardLunches:number; veganLunches:number; presentation:boolean; livestream:boolean; seriesId:string; revisionNumber:number; current:boolean};
type Room = {id:string; name:string; capacity:number; priceCents:number; version:number};
type Resource = {id:string; name:string; kind:string; stock:number; priceCents:number; version:number};
type Venue = {name:string; rooms:Room[]; bookings:Booking[]; resources:Resource[]; timezone:string};
const money = (c:number) => new Intl.NumberFormat('en-US',{style:'currency',currency:'USD'}).format(c/100);
const interval = (start:string,end:string) => `${start.replace('T',' ')} → ${end.slice(11,16)}`;
let demoIdentity='alex';
async function api<T>(path:string,body?:unknown):Promise<T> {
  const response = await fetch('/api'+path,body===undefined?undefined:{method:'POST',headers:{'Content-Type':'application/json','X-Annex-Demo-User':demoIdentity},body:JSON.stringify(body)});
  let data;
  try {data=await response.json()} catch {throw new Error('The server returned an unreadable response. Check that Spring is running.')}
  if(!response.ok) throw new Error(data.message||'Request failed');
  return data;
}
function RevisionComparison({before,after}:{before:Event;after:Event}) {
  const oldProposal=before.assessments.find(a=>a.proposal)?.proposal;
  const newProposal=after.assessments[0]?.proposal;
  const signed=(c:number)=>(c>0?'+':c<0?'−':'')+money(Math.abs(c));
  const groups:[string,(a:Allocation)=>boolean][]=[
    ['Rooms',a=>a.kind==='ROOM'],['Lunch',a=>a.kind==='LUNCH'],
    ['Presentation kit',a=>a.resourceId==='EQ-PRESENT'],['Livestream kit',a=>a.resourceId==='EQ-STREAM'],
    ['Streaming operator',a=>a.kind==='STREAM_OPERATOR'],['Catering attendant',a=>a.kind==='CATERING_ATTENDANT']
  ];
  const amount=(p:Proposal,match:(a:Allocation)=>boolean)=>p.allocations.length?p.allocations.filter(match).reduce((sum,a)=>sum+a.totalCents,0):match({kind:'ROOM'} as Allocation)?p.totalCents:0;
  return <section><h2>Compare revisions {before.revisionNumber} → {after.revisionNumber}</h2>
    <div className="table-wrap"><table><thead><tr><th>Requirement</th><th>Before</th><th>After</th></tr></thead><tbody>
      <tr><td>Date</td><td>{before.eventDate}</td><td>{after.eventDate}</td></tr>
      <tr><td>Attendees</td><td>{before.attendees}</td><td>{after.attendees}</td></tr>
      <tr><td>Standard / vegan lunches</td><td>{before.standardLunches} / {before.veganLunches}</td><td>{after.standardLunches} / {after.veganLunches}</td></tr>
      <tr><td>Presentation / livestream</td><td>{before.presentation?'Yes':'No'} / {before.livestream?'Yes':'No'}</td><td>{after.presentation?'Yes':'No'} / {after.livestream?'Yes':'No'}</td></tr>
      <tr><td>Budget</td><td>{money(before.budgetCents)}</td><td>{money(after.budgetCents)}</td></tr>
    </tbody></table></div>
    {oldProposal&&newProposal?<><div className="table-wrap"><table><caption>Validated proposal costs</caption><thead><tr><th>Item</th><th>Before</th><th>After</th><th>Change</th></tr></thead><tbody>
      <tr><td>Room assignment</td><td>{oldProposal.allocations.filter(a=>a.kind==='ROOM').map(a=>a.name).join(' + ')||oldProposal.roomName}</td><td>{newProposal.allocations.filter(a=>a.kind==='ROOM').map(a=>a.name).join(' + ')||newProposal.roomName}</td><td>—</td></tr>
      {groups.map(([name,match])=><tr key={name}><td>{name}</td><td>{money(amount(oldProposal,match))}</td><td>{money(amount(newProposal,match))}</td><td>{signed(amount(newProposal,match)-amount(oldProposal,match))}</td></tr>)}
      <tr><td>Manager credit</td><td>{signed(-(oldProposal.credit?.amountCents||0))}</td><td>{signed(-(newProposal.credit?.amountCents||0))}</td><td>{signed((oldProposal.credit?.amountCents||0)-(newProposal.credit?.amountCents||0))}</td></tr><tr><th>Total payable</th><th>{money(oldProposal.payableTotalCents)}</th><th>{money(newProposal.payableTotalCents)}</th><th>{signed(newProposal.payableTotalCents-oldProposal.payableTotalCents)}</th></tr>
    </tbody></table></div><small>Compares stored quotes, without recalculating historical prices. Earlier proposals are unavailable for acceptance.</small></>:<p className="muted">A validated proposal is needed for both revisions to compare costs. Assess the current revision; earlier results remain in history.</p>}
  </section>;
}
function App() {
  const [venue,setVenue]=useState<Venue|null>(null), [events,setEvents]=useState<Event[]>([]);
  const [identity,setIdentity]=useState('alex'),[creditSession,setCreditSession]=useState<{proposalId:string;sessionId:string}|null>(null);
  const [editing,setEditing]=useState(false);
  const [id,setId]=useState(''), [busy,setBusy]=useState(''), [error,setError]=useState('');
  const [eventType,setEventType]=useState<'MEETING'|'WORKSHOP'>('WORKSHOP');
  const [title,setTitle]=useState('Northstar customer workshop'), [date,setDate]=useState('2026-10-15');
  const [attendees,setAttendees]=useState(60), [budget,setBudget]=useState('4000');
  const [standard,setStandard]=useState(50), [vegan,setVegan]=useState(10);
  const [presentation,setPresentation]=useState(true), [livestream,setLivestream]=useState(true), [confirmed,setConfirmed]=useState(false);
  const event=events.find(e=>e.id===id), latest=event?.assessments[0], proposal=latest?.proposal;
  const isWorkshop=(event?.eventType||eventType)==='WORKSHOP';
  const previous=event?events.filter(e=>e.seriesId===event.seriesId&&e.revisionNumber<event.revisionNumber).sort((a,b)=>b.revisionNumber-a.revisionNumber)[0]:undefined;
  const assessing=busy==='Loomspan is assessing this request';
  const invalidWorkshop=eventType==='WORKSHOP'&&(attendees%2!==0||attendees<2||standard+vegan!==attendees||(livestream&&!presentation));
  async function refresh() {
    const [v,e]=await Promise.all([api<Venue>('/venue'),api<Event[]>('/events')]);
    setVenue(v);setEvents(e);
  }
  useEffect(()=>{refresh().catch(e=>setError(e.message))},[]);
  async function run(label:string,action:()=>Promise<void>) {
    setBusy(label);setError('');
    try {await action();await refresh()}
    catch(e) {setError(e instanceof Error?e.message:'Request failed');try{await refresh()}catch{/* Preserve the original error. */}}
    finally {setBusy('')}
  }
  function chooseType(type:'MEETING'|'WORKSHOP') {
    setEventType(type);setConfirmed(false);
    setTitle(type==='WORKSHOP'?'Northstar customer workshop':'Northstar team meeting');
    setAttendees(type==='WORKSHOP'?60:20);setBudget(type==='WORKSHOP'?'4000':'500');
    setStandard(50);setVegan(10);setPresentation(true);setLivestream(true);
  }
  async function create() {
    await run('Saving request',async()=>{
      const workshop=eventType==='WORKSHOP';
      const e=await api<Event>(editing?'/events/'+id+'/revisions':'/events',{title,eventDate:date,attendees,budgetCents:Math.round(Number(budget)*100),eventType,
        standardLunches:workshop?standard:0,veganLunches:workshop?vegan:0,presentation:workshop&&presentation,livestream:workshop&&livestream});
      setId(e.id);setEditing(false);
    });
  }
  const roomName=(roomId:string)=>venue?.rooms.find(r=>r.id===roomId)?.name||roomId;
  return <>
    <div className="banner">THE ANNEX DEMO <span>Real Loomspan assessment · Hibernate / H2 persistence · local demo</span></div>
    <header><strong><i>A</i>The Annex</strong><span>Event operations</span><label className="identity">Demo identity — simulated login<select value={identity} disabled={!!busy} onChange={e=>{demoIdentity=e.target.value;setIdentity(e.target.value);setError("")}}><option value="alex">Alex · coordinator</option><option value="morgan">Morgan · manager</option></select></label><button disabled={!!busy} onClick={()=>{setId('');setEditing(false);setConfirmed(false);setError('')}}>New request</button></header>
    <main>
      <div className="heading"><div><p className="eyebrow">EVENT WORKSPACE</p><h1>{event?.title||'Plan an event'}</h1><p className="muted">{isWorkshop?'Two-room workshop · lunch and optional technical services':'Room-only meeting'} · 13:00–18:00 · Pacific time</p></div><button disabled={!!busy} onClick={()=>run('Refreshing',refresh)}>Refresh records</button></div>
      {error&&<div className="error" role="alert">{error}</div>}
      <div className="workspace">
        <aside>
          <section><h2>Confirmed request</h2>
            {event&&!editing?<>
              <dl><dt>Event type</dt><dd>{isWorkshop?'Workshop':'Room-only meeting'}</dd><dt>Date</dt><dd>{event.eventDate}</dd><dt>Attendance</dt><dd>{event.attendees} people{isWorkshop&&` · two groups of ${event.attendees/2}`}</dd><dt>Budget</dt><dd>{money(event.budgetCents)}</dd>
                {isWorkshop&&<><dt>Lunches</dt><dd>{event.standardLunches} standard + {event.veganLunches} vegan</dd><dt>Technical services</dt><dd>{event.presentation?'Presentation kit':'No presentation kit'}{event.livestream?' + plenary livestream':''}</dd></>}
                <dt>Room reservation</dt><dd>12:30–18:30</dd></dl>
              <small>Revision {event.revisionNumber} · {event.current?"Current requirements":"Historical — unavailable for acceptance"}. Earlier requirements and proposals are preserved.</small><button disabled={!!busy||!event.current||event.assessments.some(a=>a.status==="BOOKED"||a.status==="RUNNING")} onClick={()=>{setTitle(event.title);setDate(event.eventDate);setAttendees(event.attendees);setBudget(String(event.budgetCents/100));setEventType(event.eventType);setStandard(event.standardLunches);setVegan(event.veganLunches);setPresentation(event.presentation);setLivestream(event.livestream);setConfirmed(false);setEditing(true)}}>Revise requirements</button>
            </>:<form onSubmit={e=>{e.preventDefault();create()}} onChange={()=>setConfirmed(false)}>
              <fieldset disabled={!!busy}>
                <label>Event type<select disabled={editing} value={eventType} onChange={e=>chooseType(e.target.value as 'MEETING'|'WORKSHOP')}><option value="WORKSHOP">Workshop</option><option value="MEETING">Room-only meeting</option></select></label>
                <label>Event title<input required maxLength={120} value={title} onChange={e=>setTitle(e.target.value)}/></label>
                <label>Date<input type="date" required value={date} onChange={e=>setDate(e.target.value)}/></label>
                <label>Attendees<input type="number" required min={isWorkshop?2:1} max={120} step={isWorkshop?2:1} value={attendees} onChange={e=>setAttendees(Number(e.target.value))}/></label>
                <label>Budget (USD)<input type="number" required min="0.01" max="100000" step="0.01" value={budget} onChange={e=>setBudget(e.target.value)}/></label>
                {isWorkshop&&<>
                  <p className="muted">Two equal discussion groups; one uses the plenary room. Theater seating throughout.</p>
                  <label>Standard lunches<input type="number" required min={0} max={120} value={standard} onChange={e=>setStandard(Number(e.target.value))}/></label>
                  <label>Vegan lunches<input type="number" required min={0} max={120} value={vegan} onChange={e=>setVegan(Number(e.target.value))}/></label>
                  <small>{standard+vegan} lunches for {attendees} attendees. Counts must match; they do not automatically scale.</small>
                  <label className="check"><input type="checkbox" checked={presentation} onChange={e=>{setPresentation(e.target.checked);if(!e.target.checked)setLivestream(false)}}/>Presentation kit</label>
                  <label className="check"><input type="checkbox" checked={livestream} onChange={e=>{setLivestream(e.target.checked);if(e.target.checked)setPresentation(true)}}/>Livestream plenaries, including operator</label>
                  <small>Lunch at 13:00. No AV in breakout rooms. Only standard/vegan portions are supported in this demo.</small>
                </>}
              </fieldset>
              {invalidWorkshop&&<p className="error">Use an even attendance and meal counts that add up to it.</p>}
              <label className="check"><input type="checkbox" checked={confirmed} onChange={e=>{e.stopPropagation();setConfirmed(e.target.checked)}}/>I confirm these requirements{isWorkshop?', theater seating and the fixed workshop agenda':', with no catering or AV'}.</label>
              <button className="primary" disabled={!confirmed||invalidWorkshop||!!busy}>{editing?"Save new revision":"Save request"}</button>{editing&&<button type="button" disabled={!!busy} onClick={()=>setEditing(false)}>Cancel revision</button>}
            </form>}
          </section>
          <section><h2>Saved requests</h2>{events.length===0?<p className="muted">No requests yet.</p>:events.filter(e=>e.current).map(e=><button className={'request '+(id===e.id?'selected':'')} key={e.id} disabled={!!busy} onClick={()=>{setId(e.id);setEditing(false)}}><b>{e.title}</b><small>{e.eventDate} · revision {e.revisionNumber} · {e.attendees} people · {e.assessments[0]?.status||'Not assessed'}</small></button>)}</section>
        </aside>
        <article>{event&&<section><h2>Requirement revisions</h2>{events.filter(e=>e.seriesId===event.seriesId).sort((a,b)=>b.revisionNumber-a.revisionNumber).map(e=><button key={e.id} disabled={!!busy||editing} className={e.id===id?"selected":""} onClick={()=>setId(e.id)}>Revision {e.revisionNumber}{e.current?" · current":" · historical"}</button>)}{!event.current&&<p className="muted">Historical proposals cannot be accepted. Open the current revision to assess or book.</p>}</section>}{event&&previous&&<RevisionComparison before={previous} after={event}/>}
          <section aria-live="polite"><p className="eyebrow">{assessing?'ASSESSING':latest?.status||'ASSESSMENT'}</p><h2>{busy||(latest?.status==='BOOKED'?'Booking saved':latest?.status==='READY'?'A validated event proposal':latest?.status==='NO_OPTION'?'No option meets these requirements':latest?.status==='FAILED'?'Assessment needs attention':'Ready to investigate the venue')}</h2>
            {busy&&<p className="muted">Please wait. Assessment can take a few minutes. Resources are not held until acceptance succeeds.</p>}
            {!event?<p className="muted">Confirm and save the request to begin a real assessment.</p>:<>
              <p>{(!assessing&&latest?.summary)||(isWorkshop?'Loomspan investigates space and catering, then checks technical services for the selected plenary room. Java validates the complete proposal.':'Loomspan uses the space specialist to find a suitable room. Java validates capacity, availability and the exact price.')}</p>
              {latest?.openQuestions.map(q=><p key={q}>{q}</p>)}
              {proposal&&<div className="proposal">
                <h3>{proposal.allocations.length?proposal.allocations.filter(a=>a.kind==='ROOM').map(a=>a.name).join(' + '):proposal.roomName}</h3>
                <p>{event.attendees} attendees · theater layout{isWorkshop&&` · plenary in ${proposal.roomName}`}</p>
                {proposal.allocations.length>0&&<div className="table-wrap"><table><caption>Validated quote</caption><thead><tr><th>Item</th><th>Calculation</th><th>Amount</th></tr></thead><tbody>{proposal.allocations.map(a=><tr key={a.resourceId}><td>{a.name}</td><td>{a.kind==='ROOM'?'Flat block':a.kind==='STREAM_OPERATOR'?`${a.priceUnits} hours × ${money(a.unitPriceCents)}`:`${a.priceUnits} × ${money(a.unitPriceCents)}`}</td><td>{money(a.totalCents)}</td></tr>)}</tbody></table></div>}
                <p>Original quote: {money(proposal.totalCents)}</p>{proposal.credit&&<p className="success">Manager room credit: −{money(proposal.credit.amountCents)} · approved by {proposal.credit.approvedBy} on {proposal.credit.approvedAt.replace("T"," ")}</p>}<strong>{money(proposal.payableTotalCents)}</strong><small>{money(event.budgetCents-proposal.payableTotalCents)} below budget</small>
                <details><summary>Supporting records and reservations</summary>
                  {proposal.allocations.length?proposal.allocations.map(a=><p key={a.resourceId}><b>{a.resourceId}</b> · version {a.version} · quantity {a.quantity}<br/>{interval(a.startsAt,a.endsAt)}{a.assignedRoomId&&a.kind!=='ROOM'&&` · ${roomName(a.assignedRoomId)}`}</p>):<p>{proposal.roomId} · room version {proposal.roomVersion} · {event.eventDate} · 12:30–18:30.</p>}
                  <p>Acceptance rechecks all selected resources under database locks. A conflict saves no partial booking.</p>
                </details>
                {!proposal.booking&&event.current&&<div className="credit-action"><p className="muted">One $100 manager credit when rooms total at least $500. Alex can try the action to see server-enforced denial.</p><button disabled={!!busy||editing||latest?.status!=="READY"||!!proposal.credit} onClick={()=>run("Requesting manager credit",async()=>{const result=await api<{credit:Credit;sessionId:string}>("/proposals/"+proposal.id+"/room-credit",{});setCreditSession({proposalId:proposal.id,sessionId:result.sessionId})})}>{proposal.credit?"Room credit applied":"Apply $100 room credit"}</button>{creditSession?.proposalId===proposal.id&&<small className="session">Credit skill session: {creditSession.sessionId}</small>}</div>}{proposal.booking?<p className="success">Booking {proposal.booking.id} is persisted. Refreshing or restarting retains it.</p>:<button className="primary" disabled={!!busy||editing||!event.current||latest?.status!=='READY'} onClick={()=>run('Reserving resources',async()=>{await api('/proposals/'+proposal.id+'/accept',{})})}>Accept & reserve {isWorkshop?'all resources':'room'}</button>}
              </div>}
              {latest?.sessionId&&<small className="session">Loomspan session: {latest.sessionId}</small>}
              <button disabled={!!busy||editing||!event.current||latest?.status==='BOOKED'||latest?.status==='RUNNING'} onClick={()=>run('Loomspan is assessing this request',async()=>{await api('/events/'+id+'/assessments',{})})}>{latest?'Run fresh assessment':'Assess with Loomspan'}</button>
            </>}
          </section>
          {isWorkshop&&<section><h2>Confirmed workshop agenda</h2><div className="schedule"><div><b>13:00–13:30</b><span>Boxed lunch and welcome</span></div><div><b>13:30–15:00</b><span>Plenary presentation</span></div><div><b>15:00–15:15</b><span>Break</span></div><div><b>15:15–16:15</b><span>Two equal discussion groups · no AV</span></div><div><b>16:15–16:30</b><span>Break</span></div><div><b>16:30–18:00</b><span>Plenary Q&A</span></div></div></section>}
          {event&&event.assessments.length>1&&<section><h2>Assessment history</h2>{event.assessments.slice(1).map(a=><div className="history" key={a.id}><b>{a.status}</b><p>{a.summary}</p><small>{a.id}</small></div>)}</section>}
          <section><h2>Venue catalog</h2><div className="rooms">{venue?.rooms.map(r=><div key={r.id}><h3>{r.name}</h3><p>{r.capacity} seats</p><strong>{money(r.priceCents)}</strong><small>Flat event block</small></div>)}</div>
            <details className="catalog"><summary>Catering, equipment and staff</summary>{venue?.resources.map(r=><p key={r.id}><b>{r.name}</b> · {r.stock} {r.kind==='LUNCH'||r.kind==='DRINKS'?'portions':'available'} · {money(r.priceCents)}{r.kind==='STREAM_OPERATOR'?'/hour':r.kind==='LUNCH'||r.kind==='DRINKS'?'/person':''}</p>)}</details>
          </section>
          <section><h2>Reservation schedule</h2><p className="muted">Includes setup and teardown · {venue?.timezone||'America/Los_Angeles'}</p><div className="schedule">{venue?.bookings.map(b=><div key={b.id}><b>{b.title}</b>{b.reservations.map(r=><span key={r.resourceId}>{r.name} · quantity {r.quantity}<small>{interval(r.startsAt,r.endsAt)}</small></span>)}</div>)}</div></section>
        </article>
      </div>
      <footer>Real meeting and workshop assessment and booking. Requirement revisions preserve proposal history. Manager credits use real authorization with simulated demo identities. Attachment intake remains a future slice.</footer>
    </main>
  </>;
}
createRoot(document.getElementById('root')!).render(<App/>);

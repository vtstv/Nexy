package signaling

import (
	"encoding/json"
	"log"

	nexy "github.com/vtstv/nexy/internal/ws"
)

type SignalingHandler struct {
	hub *nexy.Hub
}

func NewSignalingHandler(hub *nexy.Hub) *SignalingHandler {
	return &SignalingHandler{hub: hub}
}

func (h *SignalingHandler) HandleCallOffer(msg *nexy.NexyMessage) {
	var body nexy.CallOfferBody
	if err := msg.ParseBody(&body); err != nil {
		log.Printf("Error parsing call offer: %v", err)
		return
	}

	log.Printf("Call offer from %d to %d: call_id=%s",
		msg.Header.SenderID,
		*msg.Header.RecipientID,
		body.CallID)
}

func (h *SignalingHandler) HandleCallAnswer(msg *nexy.NexyMessage) {
	var body nexy.CallAnswerBody
	if err := msg.ParseBody(&body); err != nil {
		log.Printf("Error parsing call answer: %v", err)
		return
	}

	log.Printf("Call answer from %d: call_id=%s",
		msg.Header.SenderID,
		body.CallID)
}

func (h *SignalingHandler) HandleICECandidate(msg *nexy.NexyMessage) {
	var body nexy.ICECandidateBody
	if err := msg.ParseBody(&body); err != nil {
		log.Printf("Error parsing ICE candidate: %v", err)
		return
	}

	log.Printf("ICE candidate from %d: call_id=%s",
		msg.Header.SenderID,
		body.CallID)
}

func (h *SignalingHandler) HandleCallCancel(msg *nexy.NexyMessage) {
	var body nexy.CallCancelBody
	if err := msg.ParseBody(&body); err != nil {
		log.Printf("Error parsing call cancel: %v", err)
		return
	}

	log.Printf("Call cancelled by %d: call_id=%s, reason=%s",
		msg.Header.SenderID,
		body.CallID,
		body.Reason)
}

func (h *SignalingHandler) HandleCallEnd(msg *nexy.NexyMessage) {
	var body nexy.CallCancelBody
	if err := msg.ParseBody(&body); err != nil {
		log.Printf("Error parsing call end: %v", err)
		return
	}

	log.Printf("Call ended by %d: call_id=%s",
		msg.Header.SenderID,
		body.CallID)
}

func (h *SignalingHandler) HandleCallBusy(msg *nexy.NexyMessage) {
	var body nexy.CallCancelBody
	if err := msg.ParseBody(&body); err != nil {
		log.Printf("Error parsing call busy: %v", err)
		return
	}

	log.Printf("Call busy from %d: call_id=%s",
		msg.Header.SenderID,
		body.CallID)
}

func (h *SignalingHandler) CreateCallOffer(senderID, recipientID int, callID, sdp string, video, audio bool) ([]byte, error) {
	body := nexy.CallOfferBody{
		CallID: callID,
		SDP:    sdp,
		Video:  video,
		Audio:  audio,
	}

	msg, err := nexy.NewNexyMessage(nexy.TypeCallOffer, senderID, nil, body)
	if err != nil {
		return nil, err
	}

	msg.Header.RecipientID = &recipientID
	return json.Marshal(msg)
}

func (h *SignalingHandler) CreateCallAnswer(senderID, recipientID int, callID, sdp string) ([]byte, error) {
	body := nexy.CallAnswerBody{
		CallID: callID,
		SDP:    sdp,
	}

	msg, err := nexy.NewNexyMessage(nexy.TypeCallAnswer, senderID, nil, body)
	if err != nil {
		return nil, err
	}

	msg.Header.RecipientID = &recipientID
	return json.Marshal(msg)
}
